# Verifikationsbericht: slice-simulation-mehrere-anweisungen-und-ausgabewert — 2026-11-19

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, keine Änderung an `roadmap.md` oder am Lifecycle.

**Stand:** Branch `slice-simulation-mehrere-anweisungen-und-ausgabewert`, Spitze `76e2cdea` (Arbeitsbaum sauber vor diesem Bericht; `main` `091c96b0` ist Vorfahr, `git log HEAD..main` leer). Auf `HEAD` liegen die Stempel `werkzeug` und `tests` als grün (`git notes --ref=refs/notes/gates show HEAD`, gelesen, nicht wiederholt).

**Gegenstand:** [slice-simulation-mehrere-anweisungen-und-ausgabewert](../plan/planning/done/slice-simulation-mehrere-anweisungen-und-ausgabewert.md) (A-2 und Rest von A-16, Nutzerentscheidung 2026-11-18: Code angleichen, begrenzt auf erkannte Formen). Bezug: [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) (`Proposed`, Entscheidung des Nutzers steht aus). Gelesen: Slice, [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md), [Review](2026-11-19-slice-simulation-mehrere-anweisungen-und-ausgabewert.md) und [Re-Review](2026-11-19-slice-simulation-mehrere-anweisungen-und-ausgabewert-re-review.md), `git diff main...HEAD` (26 Dateien, +915 −160).

## Selbst gefahrene Sensoren (End-Stand `76e2cdea`, unter der Messsperre, Docker)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0: Jar, Tests (`--no-cache`, 1511 Zeilen PASSED, kein roter Test), detekt „BUILD SUCCESSFUL“ | 577 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 410 s)“; Stufen 1 bis 9: 77, 91, 48, 45, 15, 14, 35, 13, 72 s | 410 s |
| `make kompatibilitaet` | Exit 0, Kategorie `Szenario`: 14 bestanden; `Zwei_Anweisungen_…_zwei_Ergebnismengen` („Ids 1,2“) und `InputOutput_…_behaelt_den_Eingabewert` („Wert danach '7'“) grün | 13 s |
| `make vergleichslauf-d LAUF=mitschnitt` | Exit 0, `mitschnitt-2022` und `mitschnitt-2025`, je 324 Schritte, „gleich oder genau die gelistete Abweichung“; `mehrteilig/01/*` und `rpc-ausgabewert/01/*` je 12 mal „gleich“, ohne Regel | 33 s |
| `make vergleichslauf-d LAUF=live` | Exit 0, `live-2022` und `live-2025`, je 25 Schritte; `mehrteilig` (zwei Ergebnismengen, je eine Zeile) und `inputoutput-parameter` (Wert danach 7): „gleich“ ohne Regel | 85 s |
| Probe der Abweisungsfälle (Zusatz, Wegwerf-Kopie) | 24 Texte gegen `SimulationBackend` mit Probe-Quelle, Auswertung im Abschnitt „Abweisungsfälle“ (Befund V-1); nichts im Repo geändert | 50 s |

Gesamt rund 19,5 Minuten Messzeit; Messsperre sofort übernommen (08:26:42), Freigabe per `trap`; `available` vor jedem Lauf von einem Skript gegen 4000 MB geprüft (14,9 bis 15,1 GB). Lastmittel 5,6 (Beginn) bis 4,2 (Ende). Größte Posten: `make clean-build` (577 s), `make e2e` (410 s, Stufe 2 mit 91 s). Keine künstliche Last. `make docs-check` Exit 0 (1679 Dateien, 0 Befunde) und `make flaeche` Exit 0 nur für diesen Bericht (Stand mit Bericht). Nicht gefahren: `make gates`, `make spec-abdeckung`, Mutationen (Aufgabe des Implementers nach `harness/README.md` §Minimal agent workflow; die drei Mutationen des Implementers hat der Re-Review an den Logs gelesen, nicht wiederholt).

## Plan-vs-Code-Diff (`git diff main...HEAD`, 26 Dateien)

| Plan §3 | Diff | Urteil |
|---|---|---|
| `SeriesForms.kt`, `SelectExecution.kt` neu | beide neu: Zerlegung an `;`-Token mit `SERIES_LIMIT = 2`; Ausführung einer oder zweier Auswahlen, `echo` nach dem letzten Ergebnis | gleich |
| `StatementParser.kt`, `StatementPlanner.kt`, `Statement.kt`, `SqlScanner.kt`, `SimulationBackend.kt` update | `Recognition.Series`, `PlanResult.Series`, `TOO_MANY_STATEMENTS`, Anschluss im Backend; die alte `SelectExecution` ist aus `SimulationBackend.kt` verschoben | gleich |
| `ResponseSequencer.kt`, `RpcOutputs.kt`, `RpcTrailer.kt`, `KompatibilitaetsServer.kt` gelesen, nicht geändert | im Diff unberührt | gleich |
| vier Testklassen update, `SzenarienTests.cs` update | `StatementFormsTest`, `StatementOracleTest`, `StatementPlannerTest`, `SimulationBackendSelectTest`, `SzenarienTests.cs` im Diff | gleich |
| beide TSV-Listen update | live: `mehrteilig` entfällt; Mitschnitt: fünf `mehrteilig/01/*` und zwei `rpc-ausgabewert/01/*` entfallen (7 Zeilen); beide Läufe grün ohne sie | gleich |
| Spec, Matrix, Messprotokoll, Nutzer-Doku update | `spec/spezifikation.md` (S2, Schritte 1 und 5, Ausgabewert, Historie), Matrix Nr. 16 und 7a, `referenzabgleich.md`, `docs/user/` (3) | gleich |
| [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md), ADR-Index (Architect), beide Reports, `spec-abdeckung.md` | alle im Diff; der Slice nennt [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) in `Bezug:` und §3 (N-1 des Re-Reviews behoben) | gleich |
| nicht in §3 | die Slice-Datei selbst | Folge des Auftrags, kein Befund |

Kein Zweck- oder Plan-Verstoß im Diff: keine öffentliche Fläche geändert (`src/main` nur `internal`), kein `@Suppress`, `config/`, `Dockerfile`, `tools/` unberührt. Die Reste des Re-Reviews (N-1, N-4, N-5) sind in `76e2cdea` behoben (Grep: „auf „gemessen“ gestellt“ und „7a misst“ ohne Treffer, `0055` im Slice dreimal).

## DoD Punkt für Punkt (Haken im Slice sind leer, das ist Sache des Planners)

| DoD-Punkt | Beleg | Urteil |
|---|---|---|
| Mehrere Anweisungen je Text: Tests Happy, Grenze, Fehler in der zweiten; Live-Schritt `mehrteilig` und die fünf Regeln `mehrteilig/01/*` entfallen | Tests `zwei Auswahlen …`, `die Grenze der Anweisungszahl ist zwei`, `ein Fehler in der zweiten Auswahl` PASSED im `clean-build`; Live 2022 und 2025 und Mitschnitt je „gleich“ ohne Regel | erfüllt |
| Ausgabewert: `InputOutput` bekommt den Wert, Regeln `rpc-ausgabewert/01/*` entfallen, `OUTPUT` als „nicht gemessen“ | Mitschnitt `rueckgabe`, `tokenfolge`: RETURNVALUE Ordinal 2, Wert 7, beide Instanzen „gleich“; reiner `OUTPUT` „nicht gemessen“ | erfüllt |
| Nachweis: `LAUF=mitschnitt`, `make test` engste Klasse, Live-Lauf als Messung | Mitschnitt und Live jetzt von mir gefahren und grün (Slice §7 darf „Live-Lauf gefahren“ führen, mit dem Vorbehalt von V-3); `make test` Stufe im `clean-build` grün | erfüllt |
| `make gates`, engste Testklasse, Mutationen, `docs-check`, `flaeche`, `kopplung-zahlen`, `spec-abdeckung` (Implementer); Verifier: `clean-build`, `e2e` | `clean-build` und `e2e` grün (oben); die übrigen: Stempel `werkzeug` und `tests` auf `HEAD` grün, Mutationen nach Re-Review-Log | Verifier-Teil erfüllt, Rest nicht geprüft (Zuständigkeit) |
| Review, kein Self-Review | Review und Re-Review liegen vor (Reviewer-Kontext), kein Befund HIGH offen | erfüllt |
| Doku-Update öffentlicher Vertrag | alle genannten Dateien im Diff; Treffer auf „A-2“ und „A-16“ außerhalb von `done/` und Reports nur in Slice, Messprotokoll-Abschnitt und Testkommentar | erfüllt, Reste V-1, V-3 |
| Closure-Notiz, Register, Risiken §6, Paarungen | §7 „Wird bei der Closure gefüllt“, Risiken „offen bis zur Closure“ | offen (Planner-Closure) |
| (nicht in der DoD) Entscheidung des Nutzers über [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) | `Proposed` in ADR, Index und Slice §4 | offen, siehe V-5 |

## Abweisungsfälle (SPEC-036 Form S2, [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) Entscheidung 4)

Probe: ein Probe-Test in einer Wegwerf-Kopie des Stands (`git archive HEAD`, Scratchpad, Dockerfile-Stage `test` mit `--tests`), `SimulationBackend` mit einer Tabelle `person` und einer Zeile; je Text der erste Fehler und die Zahl der Quellen-Öffnungen. Spalten „Spec/ADR“ nach Wortlaut, „Code“ gemessen.

| Fall (Text, gekürzt) | Spec und ADR sagen | Code liefert | Urteil |
|---|---|---|---|
| zwei Auswahlen, bekannte Tabelle (mit `;` am Ende, mit `WHERE`) | zwei Ergebnisse | `ResultStart Row ResultEnd` je Auswahl, 2 Öffnungen | gleich |
| dritte Auswahl, Tabellen bekannt | `TDS_UNSUPPORTED_SQL` | `TDS_UNSUPPORTED_SQL` („TOO_MANY_STATEMENTS“), 0 Öffnungen | gleich |
| dritte Auswahl, dritte Tabelle unbekannt | `TDS_SIM_UNKNOWN_OBJECT` (Parser erkennt, Prüfung gegen die Beschreibung) | `TDS_UNSUPPORTED_SQL` | **Abweichung (V-1)** |
| Auswahl und `INSERT`/`UPDATE`/`DELETE`/`SELECT 1`/`SET`, in beiden Stellungen; zwei `INSERT` | `TDS_UNSUPPORTED_SQL` | `TDS_UNSUPPORTED_SQL` („UNEXPECTED_TOKEN“), 0 Öffnungen | gleich (bekannte Tabellen) |
| leeres Stück (`a;;`, `;a`, `a; ;b`, `a; /* x */ ;`, `;`) | `TDS_UNSUPPORTED_SQL` (Parser erkennt, Backend weist ab) | `TDS_UNSUPPORTED_SQL` („UNEXPECTED_TOKEN“, bei `;` „EMPTY_TEXT“) | gleich im Code; Begründung der Spec trifft nicht zu (V-1) |
| zwei Auswahlen mit Parametern, Tabellen bekannt | `TDS_UNSUPPORTED_SQL` nach der Prüfung | `TDS_UNSUPPORTED_SQL` („Mehrere Anweisungen mit Parametern nicht verfügbar“), 0 Öffnungen | gleich |
| zwei Auswahlen mit Parametern, zweite Tabelle oder Spalte unbekannt | `TDS_SIM_UNKNOWN_OBJECT` | `TDS_SIM_UNKNOWN_OBJECT` („Tabelle nicht beschrieben“, „Spalte nicht beschrieben“) | gleich |
| zwei Auswahlen ohne Parameter, erste oder zweite Tabelle, Spalte unbekannt | ganzer Text abgewiesen, `TDS_SIM_UNKNOWN_OBJECT` | `TDS_SIM_UNKNOWN_OBJECT`, 0 Öffnungen | gleich |
| einzelne Auswahl mit Parameter | unverändert | Ergebnis, 1 Öffnung | gleich |

Kurz: überall wo der Parser die Folge erkennt (zwei Auswahlen, auch mit Parametern), gilt die Reihenfolge Prüfung gegen die Beschreibung vor Abweisung wie beschrieben. Bei dritter Auswahl, anderer Folge und leerem Stück weist der Parser selbst ab (`Recognition.NotRecognized`), vor jeder Prüfung gegen die Beschreibung.

## §3.8 und Entscheidungs-Konformität

- **Aussagen gegen Beleg:** Zeile S2 trennt gemessen (Mitschnitt `mehrteilig`, `DONE` mit `MORE`/`COUNT`, Befehl `0x00c1`) von Festlegungen; [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) nennt die gemessene Bedingung (`sp_executesql`, ein Parameter, `INTN` 4) im selben Satz; Nutzer-Doku gleich. Die KDocs der neuen Dateien (`SeriesForms`, `SelectSeries`, `SelectExecution`) tragen Grenze und Messlage im Indikativ. Einschränkung: die Begründung „erkennt der Parser“ in der Liste „Nicht erkannt“ ist für drei der vier Fälle breiter als der Code (V-1).
- **ADR-Konformität:** keine `Accepted`-ADR geändert ([AGENTS.md](../../AGENTS.md) §3.5); [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) und [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) unverändert, der Bau schärft sie nur über die `Proposed`-ADR. Zeile S2, Obergrenze zwei und „ohne Parameter“ stimmen mit [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) Entscheidung 2 und 3 und mit dem Code überein.
- **Sonst:** §3.1 Docker-only (alle Läufe über `make`), §3.2 kein `@Suppress`, §3.4 `spec/architecture.md` unberührt, §3.7 Kommentare im Indikativ.

## Befunde

- **V-1 · MEDIUM · Spec und ADR beschreiben für drei von vier Abweisungsfällen einen anderen Weg als der Code.** [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Liste „Nicht erkannt“) und [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) Entscheidung 4 sagen: dritte Auswahl, andere Folge, leeres Stück und zwei Auswahlen mit Parametern „erkennt der Parser“, das Backend weist sie „nach der Prüfung gegen die Beschreibung“ ab, bei nicht beschriebener Tabelle oder Spalte mit `TDS_SIM_UNKNOWN_OBJECT`. Der Code erkennt nur die Parameter-Fälle so (`PlanResult.Series`, dann `SelectSeries.prepare`); dritte Auswahl, andere Folge und leeres Stück sind `Recognition.NotRecognized` und enden vor der Prüfung mit `TDS_UNSUPPORTED_SQL`. Beobachtbar ist der Unterschied nur, wenn zugleich ein Objekt unbekannt ist (Probe: `…; …; SELECT id FROM nothing` liefert `TDS_UNSUPPORTED_SQL`, die Spec nach Wortlaut `TDS_SIM_UNKNOWN_OBJECT`); kein Test hält diese Kombination fest, die Tests der dritten Auswahl und der anderen Folge nutzen bekannte Tabellen. Entscheidung, welche Seite gilt (Text an den Code oder Code an den Text), liegt beim Planner und, solange [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) `Proposed` ist, beim Nutzer. Der Re-Review hat die Lesart „nicht erkannt“ gegen „erkannt, abgewiesen“ nur für den Parameter-Fall gesehen (N-2).
- **V-2 · LOW · Fitness Function der ADR nennt das falsche Target für den Mitschnitt-Lauf.** [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) führt für „Mitschnitt-Lauf (kein Gate)“ `make referenztest-dotnet`; das ist der Smoke-Test des Clients ohne Server (`tools/mk/referenztest-dotnet.mk`), den Mitschnitt spielt `make vergleichslauf-d LAUF=mitschnitt` ab (Kategorie `MitschnittAbspielen`). Die ADR ist `Proposed` und noch änderbar.
- **V-3 · INFO · Die Zeilen „Live-Lauf nicht gefahren“ und „Lauf steht aus“ sind nach diesem Bericht überholt.** Matrix Nr. 16 und 7a, der neue Abschnitt in `docs/messungen/referenzabgleich.md` und das „Akzeptierte Negativ“ der ADR nennen den Live- und den Referenzclient-Lauf als nicht gefahren. Jetzt gefahren (siehe oben): Live-Schritt `mehrteilig` 2022 und 2025 „gleich“ (Referenz und Server liefern je zwei Ergebnismengen mit je einer Zeile), `make kompatibilitaet` mit „Ids 1,2“. Grenze: der Live-Schritt `inputoutput-parameter` und der Test `InputOutput_…` sehen nur „Wert danach 7“, nicht das RETURNVALUE; das RETURNVALUE belegt allein der Mitschnitt `rpc-ausgabewert` gegen die Aufnahmen (je Instanz ein Fall), nicht eine frische Abfrage des echten Servers. Nachführen ist Sache des Planners oder Implementers.
- **V-4 · INFO · Die Mitschnitt-Läufe belegen Wiedergabe gegen Aufnahmen, keine neue Messung.** `mehrteilig/01/*` und `rpc-ausgabewert/01/*` vergleichen die Antwort des Servers mit den aufgenommenen Bytes der Referenz (je Instanz ein Fall); die Zulassung anderer Paare der Form S1 und das Verhalten bei Fehlern der zweiten Auswahl bleiben Festlegungen und sind als solche gekennzeichnet (kein Befund gegen §3.8).
- **V-5 · INFO · Entscheidung über [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) ist keine DoD-Bedingung** (wie N-8 des Re-Reviews). Alle Haken der DoD sind leer; verwirft der Nutzer die Lesart, tragen Spec-Zeile S2, Code und Tests eine Form ohne Träger. Planner: Closure-Reihenfolge.
- **V-6 · INFO · Mutationen und Gates nicht von mir gefahren.** Die Zusagen von `src/` stützen sich auf die drei Mutationen des Implementers (Re-Review N-7: der zweite Teil des Tests „nach einem Fehler der ersten Quelle …“ ist im Mutationslauf nicht erreicht worden) und auf die Stempel auf `HEAD`.

Kein Befund der Klasse HIGH, keine DoD-Verletzung im Verifier-Teil; ein MEDIUM (V-1, Text gegen Code, kein Laufzeitfehler bei bekannten Tabellen).

## Ergebnis

Der Diff entspricht dem Plan §3 ohne Rest. `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d LAUF=mitschnitt` und `LAUF=live` sind auf `76e2cdea` grün; die Live-Schritte `mehrteilig` (beide Instanzen gleich) und `inputoutput-parameter` (Wert 7) und die Mitschnitt-Regeln `mehrteilig/01/*` und `rpc-ausgabewert/01/*` (RETURNVALUE mit Ordinal 2, Wert 7) sind belegt, die Ausnahmen A-2 und A-16 zu Recht entfernt. Abweichung: das beschriebene Fehlerverhalten bei unbekanntem Objekt gilt für zwei Auswahlen mit und ohne Parameter, nicht für dritte Auswahl, andere Folge und leeres Stück (V-1). Offen sind die Closure-Handlungen des Planners (Haken, §7, Register, Risiko-Ausgänge), die Entscheidung des Nutzers über [ADR-0055](../plan/adr/0055-zwei-auswahlen-ohne-parameter-in-einem-text-sind-erkannt.md) und das Nachführen der Zeilen „Lauf nicht gefahren“ (V-3).
