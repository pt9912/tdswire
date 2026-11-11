# Verifikationsbericht: slice-unbekannte-tabelle-class-16-angleichen (HEAD fcf680d3)

* Rolle: Verifier (Modul 11), frischer Kontext, mit kritischer Diff-Lesung anstelle eines eigenen Reviews (Entscheid des Orchestrators: kleiner Slice, etwa 80 Zeilen netto). Gegenstand: [slice-unbekannte-tabelle-class-16-angleichen](../plan/planning/done/slice-unbekannte-tabelle-class-16-angleichen.md), Zweig `worktree-agent-a89aa48448f42a9cf`, Commits `6e813d54` (Code, Tests, Ausnahmelisten) und `fcf680d3` (Spec, Doku, Messprotokoll, Matrix, Register, Plan, Roadmap, spec-abdeckung) nach vier reinen Move-Commits.
* Maßstab: DoD §2, Plan §3, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), SPEC-036 §4, SPEC-038; Hard Rules `AGENTS.md` §3.1 bis §3.8. Grundsatz: die Spezifikation ist Zielbild, der echte SQL Server die Referenz.
* Am Produktivcode nichts geändert. Mutationen liefen in einer Wegwerf-Kopie (`git archive HEAD`) im Scratchpad. Basis `main` ist inzwischen `7ed6e93b` (Merge der ADR zur öffentlichen Fläche der Katalog-Elemente); `git merge-tree --write-tree HEAD main` ist konfliktfrei (Tree `1d385b75`). Die Läufe unten gelten für den Stand vor dieser Zusammenführung.

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (einmal, auf `fcf680d3`) | Exit 0, alle Gradle-Stufen `BUILD SUCCESSFUL`, 1315 `PASSED`, kein roter Testfall; 587 s |
| `tools/matrix-zitate.sh` Matrix gegen das clean-build-Log | Exit 0: 1315 bestandene, 349 Zitate, 0 ohne PASSED, 0 Fremdformen (Zitate unverändert, kein `make matrix-zitate` nötig) |
| `make e2e` | Exit 0, 8 Stufen grün, 353 s (Stufe 1 71 s, 2 95 s, 3 53 s, 4 48 s, 5 15 s, 6 18 s, 7 38 s, 8 15 s); Szenario 13/13, Synchronitaet 10/10, Anmeldung 19/19, Luecken 7/7 |
| `make vergleichslauf-d LAUF=mitschnitt-2022` | Exit 0, 22 s, „Mitschnitt-Vergleich: gleich oder genau die gelistete Abweichung“ (324 Schritte); kein EULA-Pull, kein Live-Lauf gegen echten SQL Server (nicht nötig) |
| `make gates FORCE=1` | Exit 0 in 47 s; **Cache-Falle:** die Stage `test` kam aus dem Cache des clean-build (identische Quellen), der Lauf belegt die Verdrahtung, den Frischbeleg trägt `make clean-build`. Darin `docs-check` (1330 Dateien, 0 Befunde), `flaeche`, `detekt`, `kopplung-zahlen`, `gate-zuordnung`, Stempel-Selbsttests |
| `make flaeche` | Exit 0 (auch im Gate-Lauf) |
| Mutationen (5, einzeln, enge Testklassen, Wegwerf-Kopie) | alle 5 rot, siehe unten |
| Merge-Probe gegen `main` `7ed6e93b` | konfliktfrei |

Logs liegen im Scratchpad `/tmp/claude-1000/-Development-tdswire/3768ddc4-cff6-4152-b0be-815e9ed5f5cb/scratchpad/v/` (nicht im Repo).

## Mutationen („Rot gesehen“)

| # | Mutation | Ergebnis |
|---|---|---|
| M1 | `TDS_SIM_UNKNOWN_OBJECT` Class 20 (`FATAL`) und offen | rot: `ErrorMapperTest > TDS_SIM_UNKNOWN_OBJECT ist ERROR 208 Class 16 und bleibt offen`, `… Gruppen der Entscheidung und Grundregel …`, `… Verbindungsfolge und Severity folgen der Schwelle 19`; `MergeFehlerEmbeddingTest > eineUnbekannteTabelleEndetMit208OhneErgebnisUndLaesstDieVerbindungOffen` |
| M2 | Class 16 (`REQUEST`) und `CLOSE_AFTER_REPLY` | dieselben vier Testfälle rot |
| M3 | `.NET`: im Test „Class 20“ `SqlDbType.Xml` durch `NVarChar` ersetzt | rot: `Ein_Fehler_der_Class_20_laesst_die_naechste_Anfrage…` „No exception was thrown“ (9 von 10 grün) |
| M4 | `TDS_UNSUPPORTED_TYPE` Class 16 und offen (Test unverändert) | rot: derselbe Test, Class erwartet 20, war 16 |
| M5 | `TDS_SIM_UNKNOWN_OBJECT` zurück auf Class 20 und schließend | rot: `SzenarienTests.Unbekannte_Tabelle_ist_ein_Fehler_vor_den_Zeilen_und_die_Verbindung_bleibt_offen` (erwartet 16, war 20; 12 von 13 grün) |

M1 und M2 trennen die zwei Zusagen (Class, Folge) je in zwei Testklassen. Der Gruppen-Test fängt beide, der Handvektor auch.

## Gesamturteil

**Closure-fähig: ja, unter B1 und B2 unten** (beide ohne Codeänderung; B1 ist eine Doku-Berichtigung an einer zweiten Fundstelle, B2 betrifft die DoD-Zeile „Review“). Code, Tests und Ausnahmelisten tragen und sind in eigenen Läufen belegt. Es gibt kein HIGH.

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| (1) | Entscheidung, ob Class 16 und offen; Folge-ADR nötig? | **erfüllt**, keine Folge-ADR | Entscheid ist die umkehrbare Standardantwort des Nutzers (2026-11-09). [ADR-0007](../plan/adr/0007-fehler-und-attention.md) geprüft (siehe unten): sie legt Class 20 nur für `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` fest und delegiert jeden weiteren Code ausdrücklich an das Fehler-Mapping; R05 (Zeile 40) misst für 208 Class 16 und offen. Die Änderung stützt sich auf die ADR, sie widerspricht ihr nicht |
| (2) | Bau und Tests | **erfüllt** | `ErrorCode.kt` (REQUEST, OPEN), `ErrorMapperTest` (Handvektor ERROR 208 Class 16, `OPEN`; Gruppen-Test), `MergeFehlerEmbeddingTest` (Bytes `D0 00 00 00 01 10`, DONEPROC-Ende, Folgeanfrage auf derselben Verbindung mit `INSERT` und `inserted.size == 1`); M1 und M2 rot |
| (3) | Spec, Doku, Register | **erfüllt mit einer Lücke (V-1)** | Spec §4 (Grundregel-Satz, Zeile, Änderungsverzeichnis), `SPEC-036`, `SPEC-038`, Messprotokoll A-3 und M-13 bis M-15, Matrix (zwei von drei Stellen), `e2e-abdeckung.md`, `docs/user/` (zwei von fünf Dateien berührt, die übrigen drei enthalten keine Fundstelle), Register `state.md` und `observation.md`, Plan §3 und §6 |
| – | `make gates` grün | **erfüllt** | siehe Läufe |
| – | Review durchgeführt, Report liegt vor | **nicht erfüllt im Wortlaut (B2)** | Kein eigener Review-Report (Entscheid des Orchestrators); dieser Bericht enthält die Diff-Lesung. Ob das die Zeile schließt, entscheidet der Planner |
| – | Haken nur durch den Planner | **eingehalten** | alle Haken `[ ]`, §7 „Wird bei der Closure gefüllt“ |
| – | Doku-Update bei öffentlichem Vertrag | **erfüllt** | `docs/user/referenz-konfiguration.md` (Tabelle Zeile `TDS_SIM_UNKNOWN_OBJECT` 208, 16, offen, Satz zu A-3, MERGE-Zeile) und `einbettung-und-betrieb.md` (Fehlertabelle; die Zeile „der Client sieht …, die Verbindung schließt“ entfällt zu Recht) stimmen mit Code und Spec; `einbettung-und-betrieb.md` enthält keine weitere Stelle zu 208 |
| – | Risiken §6 tragen einen Ausgang | **offen, für den Planner** | beide stehen „offen bis zur Closure“, siehe B3 |
| – | Drei Paarungen (Anker · Folge-Slice · Register) | **Sache der Closure** | Register fortgeschrieben; Anker und Folge-Slice entstehen mit §7 |

## ADR-0007 und die Frage der Folge-ADR

Selbst geprüft. Zeile 132 (Class der eigenen Fehlercodes): Anfragefehler Class 16 und offen; Class 20 nur `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR`; „die Zuordnung jedes weiteren Codes legt der Slice für das Fehler-Mapping fest; diese ADR legt sie nicht fest“. Dieselbe Aussage in Zeile 204 („Entschieden“). R05 (Zeile 40): `SELECT * FROM dbo.nope` liefert ERROR 208 Class 16, die Verbindung bleibt offen. Der Befund des Implementers stimmt. **Eine Folge-ADR ist nicht nötig:** die ADR bleibt unverändert (`AGENTS.md` §3.5 gewahrt), das Fehler-Mapping setzt, wie delegiert. Die Spec benennt die Zuordnung in §4 selbst. Der Plan-Eintrag „entfällt“ ist belegt. Einzige Spannung: Zeile 169 der ADR (Contra von Option A) nennt 208 als Fehler, den Anwendungen abfangen, und passt jetzt eher zur gewählten Option B; kein Widerspruch.

## Plan gegen Diff

Alle Zeilen der Plan-Tabelle §3 sind im Diff, und der Diff enthält nichts außerhalb der Tabelle (zusätzlich nur die reinen Link-Nachzüge der Move-Commits in done/-Slices, Welle und Registern, je eine Zeile). `harness/README.md` ist nicht berührt (Zellenlänge unverändert). `make flaeche` grün (keine öffentlichen Typen berührt).

## Findings

**V-1 (MEDIUM) Zweite Fundstelle in der Kompatibilitätsmatrix nicht nachgezogen.** `docs/plan/kompatibilitaetsmatrix.md` Zeile 62 (Fehlerantwort, `ErrorMapper`) nennt weiter „die Simulationscodes 208 (Class 20, schließt), 245 und 2628 (Class 16, offen)“. Das widerspricht Code, Spec und der Matrix-Zeile 220. Die Beleg-Spalte derselben Zeile sagt „die Nummern 18456, 208 und 245 sind die des SQL Servers und am Produkt nicht gemessen“; für 208 ist das nach M-15 und A-3 überholt (Nummer, Class, Folge gemessen). `AGENTS.md` §3.8: die Aussage trägt weiter als ihr Beleg. Der Gate-Lauf fängt es nicht (`make flaeche` und `docs-check` prüfen es nicht). Fix: Wortlaut berichtigen, Beleg-Spalte auf „208 Nummer, Class 16 und Folge gemessen, der Text ist Festlegung“. Kein Codezugriff.

**V-2 (LOW) Neuer Auslöser der Class-20-Aussage ohne Messzeile im Rot-gesehen-Absatz.** Der Absatz „Rot gesehen (einmalig, 2026-11-01)“ der Matrix verlor den Satz zum Class-20-Test mit 208, bekam aber keine Aussage zum `xml`-Auslöser. Dieser Bericht liefert die Belege (M3 und M4 oben, 2026-11-11); der Planner kann sie als „Rot gesehen“ in die Matrix übernehmen oder darauf verweisen. Der Wortlaut „requires an open and available Connection“ ist für den neuen Auslöser jetzt gemessen: `make e2e` Stufe 7 meldet `InvalidOperationException: ExecuteScalar requires an open and available Connection. The connection's current state is closed.; Zustand Closed`. Die Matrix-Zeile „Fehler der Class 20 (`xml`-Parameter, 50003)“ trägt das Zitat damit wörtlich (Teilzeichenkette); „es kommt keine Antwort“ ist durch dieselbe Meldung gedeckt. Aussage ≤ Beleg: erfüllt.

**V-3 (LOW) Kommentar der Ausnahme `fehler-vor-zeilen` in `live-ausnahmen.tsv`.** Die Server-Spalte (`Class 16 … Meldung 'Unknown object.'; Zustand Open; Folgeabfrage 1`) ist aus dem Code abgeleitet und nicht gegen einen Live-Lauf gefahren. Sie stimmt mit der Messzeile des .NET-Tests aus `make e2e` überein (`Number 208, Class 16, State 1, Meldung 'Unknown object.'; Verbindung danach Open`, Folgeabfrage gelingt) und die Referenzspalte ist unverändert aus dem Messprotokoll. Der Messprotokoll-Text A-3 sagt das offen („nach der Angleichung nicht gefahren“): zulässig, ehrlich. Ein Live-Lauf (EULA freigegeben) wäre nur Bestätigung; ich habe ihn nicht gefahren, weil der Mitschnitt-Lauf die Referenzspalte und die Serverseite gemeinsam deckt.

**V-4 (INFO) Mitschnitt-Ausnahmen.** `mitschnitt-ausnahmen.tsv`: Der Lauf bestätigt das Muster genau: `fehler-vor-zeilen/01/fehler` ist die einzige Ausnahme (Lücke, Text und `Server leer`), `fehler-vor-zeilen/02/*` (alle zwölf Kanäle) sind „gleich“, Folgeabfrage gelingt. Der Entfall der sechs Zeilen `02/*` ist richtig, der Text der Ausnahme (A-3) stimmt mit dem Lauf. Nur `mitschnitt-2022` gefahren; die Zeilen sind nicht instanzabhängig (kein `@2025`-Suffix), die 2025-Fassung ist nicht gefahren.

**V-5 (INFO) Prüfpunkte ohne Befund.** Code: `ErrorCode.kt`-KDoc schreibt Herkunft „gemessen“ nur für Nummer, Class, State und Folge, den Text als Festlegung (§3.8, §3.7 erfüllt: Indikativ, keine Chronik). `UNSUPPORTED_TYPE` bleibt Class 20 mit Hinweis „offen“. `MergeFehlerEmbeddingTest`: Wächter vollständig (kein COLMETADATA/INFO, Nummer, Class, Folgeanfrage), `@spec TDS-QA-01` ergänzt. Detekt grün, kein `@Suppress`. Kein Jackson, kein Host-Gradle (Docker-only). `spec-abdeckung.md`: SPEC-038 von 9 auf 10 Tests mit `ErrorMapperTest`, zwei neue Zeilen `TDS-QA-01`, stimmig zu den Tags; kein Lauf von `make spec-abdeckung` durch mich (kein Gate). `docs/user/` hat fünf Dateien; nur `referenz-konfiguration.md` und `einbettung-und-betrieb.md` nennen den Code, die übrigen drei (`README.md`, `referenz-ereignisse.md`, `testserver-manuell.md`) enthalten keine Class-20-Aussage zu 208 (grep ohne Treffer).

**Negativbefund (Pflicht):** Gesucht und nicht gefunden: weitere Stellen mit „208“ mit Class 20 in `spec/`, `docs/user/`, `docs/plan/` (nur V-1); weitere Tests, die Class 20 für `TDS_SIM_UNKNOWN_OBJECT` voraussetzen (`SimulationBackend*Test`, `StatementPlannerTest`, `ResponseMergeTest`, `TableStoreTest` prüfen den Code, nicht die Class); Suppression; öffentliche Fläche berührt; Haken gesetzt; Widerspruch zu [ADR-0007](../plan/adr/0007-fehler-und-attention.md); Änderung am Lastenheft. Nicht geprüft: Live-Lauf gegen echten SQL Server, Verhalten von mssql-jdbc nach Class 16 für 208, `mitschnitt-2025`.

## Risiken §6 (Vorschlag für die Ausgänge, Entscheidung beim Planner)

1. Tests und Spec-Stellen mit Class 20: **eingetreten und behoben**; Befund vollständig bis auf V-1 (Matrix Zeile 62); die .NET-Tests und die Ausnahmelisten sind jetzt gefahren (`make e2e` grün, Mitschnitt-Lauf grün).
2. Verhalten der Clients nach Class 16: **weiter offen** für mssql-jdbc; der Referenzclient ist für 208 jetzt gemessen (`SzenarienTests`: State Open, Folgeabfrage 1). Das Register `client-schliesst-bei-class-20-selbst` bleibt die Heimat.

## Bedingungen für die Planner-Closure

* **B1:** V-1 nachziehen (Matrix Zeile 62, Wortlaut und Beleg-Spalte); optional V-2 (Rot-gesehen-Satz mit M3 und M4 vom 2026-11-11). Dann `make docs-check` und ein weiterer `make gates` (Doku-Änderung, kein Code).
* **B2:** Entscheid, ob dieser Bericht die DoD-Zeile „Review durchgeführt“ trägt (kein eigener Reviewer; Diff kritisch gelesen, ein MEDIUM gefunden).
* **B3:** Ausgänge der beiden Risiken in §6 setzen (Vorschlag oben); die Haken der drei Liefer-Punkte und der Nachweis-Pflichten setzt der Planner.
* **B4:** Closure-Notiz §7 mit Steering-Loop-Lerneintrag und den drei Paarungen; Lerneintrag-Kandidat: eine Zuordnung in einer Tabelle mit zwei Fundstellen (Matrix Zeile 62 und 220) driftet, weil kein Sensor die Zuordnung `Code → Class` über Dokumente hält (Beobachtung 1×, unter der Schwelle). Register `unbekannte-tabelle-class-20-statt-16-am-server` auf den Stand ziehen (Lücke nur noch der feste Text); die Basis ist inzwischen `7ed6e93b`, Merge konfliktfrei.
