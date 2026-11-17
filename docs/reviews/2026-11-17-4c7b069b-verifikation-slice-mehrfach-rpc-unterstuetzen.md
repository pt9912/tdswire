# Verifikationsbericht: slice-mehrfach-rpc-unterstuetzen — 2026-11-17

**Rolle:** Verifier (Modul 11): DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Nicht Review, nicht Validierung.

**Gegenstand:** Zweig `slice-mehrfach-rpc-unterstuetzen`, Spitze `4c7b069b`, Basis `b3f4d7aa`; Plan: [slice-mehrfach-rpc-unterstuetzen](../plan/planning/in-progress/slice-mehrfach-rpc-unterstuetzen.md). Entscheidungen: [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (`Accepted`, ersetzt [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) Entscheidung 3 teilweise), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), `SPEC-011`, `SPEC-013`, `SPEC-030`; Messprotokoll [Mehrere RPCs in einer Nachricht](../messungen/referenzabgleich.md#mehrere-rpcs-in-einer-nachricht-messungen-vom-2026-11-17) (W-10 bis W-13). Anforderungen: [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität).

**Reviews im Zweig:** [Review](2026-11-17-b898101e-slice-mehrfach-rpc-unterstuetzen.md), [Re-Review](2026-11-17-13ef2e4e-slice-mehrfach-rpc-unterstuetzen-re-review.md). Beide gelesen; ihre Aussagen sind hier nicht übernommen, sondern am Stand und am Lauf geprüft.

**Eigener Lauf (Vordergrund auf dem End-Stand `4c7b069b`, sauberer Baum, keine künstliche Last; ein Lasttest-Slice lief parallel auf dem Host, Speicher frei 3 GB plus 12 GB Cache):**

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0; Jar, Tests (`--no-cache`, 1448 `PASSED`-Zeilen von Haupt- und Testserver-Modul, kein roter, kein übersprungener Testfall; übersprungen sind nur Gradle-Aufgaben `checkKotlinGradlePluginConfigurationErrors`), detekt | 605 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 417 s)“ | 417 s |
| `make gates` | Exit 0; Klasse `werkzeug` und `stempel` mit Beleg übersprungen, Klasse `tests` ohne Beleg, also gelaufen (Schichten aus dem Cache der beiden vorigen Läufe); .NET-Referenztests 171 bestanden, 0 rot, 0 übersprungen | 21 s |
| `make docs-check` (vor dem Bericht) | Exit 0, 1557 Dateien, 0 Befunde | wenige s |

Stufen von `make e2e` (Selbstangabe des Laufs): 1 Smoke 75 s, 2 Laufzeittest 94 s, 3 Simulation 53 s, 4 Szenario 51 s, 5 Anmeldung 16 s, 6 Lücken 16 s, 7 Synchronität 34 s, 8 eigenes Backend 16 s, 9 Lasttest 62 s (Probengröße). Größte Zeitposten: `clean-build` (Gradle-Test-Stage kalt, rund 3,5 Minuten je Stage), `e2e` Stufen 1 und 2 (Images).

Nicht gefahren (von der Tabelle der Änderungsart nicht verlangt): Mutationen je Zusage, `make spec-abdeckung`, `make matrix-zitate`, `make vergleichslauf-d` (EULA, echter Server). Die Messung am echten Server habe ich also nicht wiederholt; ihr Beleg sind die abgelegten Mitschnitte (siehe DoD 1).

---

## Gemessenes Ergebnis der Stufe 6 (`UpdateBatchSize 2` am Serverprozess)

Stufe 6 (`make kompatibilitaet KATEGORIE=Luecken`) lief in diesem Lauf zum ersten Mal mit dem neuen Test, 16 s, grün:

- `LueckenTests.Mehrere_RPCs_in_einer_Nachricht_gelingen_mit_UpdateBatchSize_2`: bestanden (22 ms); Messzeile: `UpdateBatchSize 2: Update meldet 3; Verbindung danach Open`.
- `LueckenTests.Einzelne_RPCs_desselben_Adapters_ohne_Batch_gelingen`: bestanden; Messzeile: `UpdateBatchSize 1: Update meldet 2; Verbindung danach Open`.
- Kategorie `Luecken`: `Total tests: 7, Passed: 7`.
- Log-Prüfung der Stufe in `tools/kompatibilitaetstest.sh`: `ok   Log: kein request_failed mit TDS_PROTOCOL_ERROR (mehrere RPCs gelingen)`; ebenso ok: Server läuft nach den Tests, `login_accepted`, kein Handlerfehler, kein Kennwort im Log.

Damit stimmen die Zahlen mit der „Erwartung“ der Matrix überein (Update meldet 3 bei drei Zeilen in Gruppen zu 2 und 1, Verbindung `Open`; `UpdateBatchSize` 1 meldet 2). Das Ergebnis ist eine Einzelmessung eines Laufs auf einem Host mit `Microsoft.Data.SqlClient` 7.1.1.

**Die „Erwartung“-Texte sind nach diesem Lauf zu aktualisieren (Aufgabe des Planners, hier nicht geändert).** Fundstellen mit „steht aus“ oder „Erwartung“ zu Stufe 6:

- `docs/plan/kompatibilitaetsmatrix.md`, Zeile 4 der Lückenliste („erwartet, dass `UpdateBatchSize` 2 … gelingt; sein Lauf … steht aus, die Zahlen in 7c sind bis dahin die Erwartung des Tests“), Abschnitt 7c, Zeile „Mehrere RPCs in einer Nachricht (Lücke 4)“ („Erwartung des Tests … der Lauf steht aus“; Spalte „Erwartung bestätigt“: „Erwartung: kein `request_failed` …“), und der Stand-Satz des Abschnitts 7 („Stand: 2026-10-31, ein Lauf je Zeile“) für diese Zeile.
- `tools/e2e.sh` Zeile 55 und `docs/plan/e2e-abdeckung.md` Zeile 28 (beide Texte müssen gleich bleiben): „Erwartung der Stufe, dass sie gelingen … (Lauf der Stufe 6 mit diesem Test steht aus …)“.
- `tools/kompatibilitaetstest.sh`: Kommentar „der Lauf gegen den Serverprozess steht aus“ und die Prüfzeile, die den Zusatz „(mehrere RPCs gelingen)“ führt (sie ist grün).

Der Re-Review hat das als N-1 erwartet; dieser Lauf löst die Bedingung.

---

## Befund je DoD-Punkt

Haken setze ich nicht (Auftrag); „erfüllt“ heißt: der Beleg trägt die Zusage am End-Stand.

| DoD-Punkt | Befund | Beleg |
|---|---|---|
| 1 Messung | erfüllt, mit benannter Grenze | Messprotokoll-Abschnitt mit W-10 bis W-13 (`docs/messungen/referenzabgleich.md`, Zeilen 141 bis 154); Mitschnitte `rpc-batch-{dml,drei,ausgabewert,fehler}.messung` unter `referenztest-dotnet/Mitschnitt/messung/2022/` und `/2025/` (je vier Dateien im Diff); Erzeuger im Repo (`Vergleich/MessungWireTests.cs`, `Mitschnitt/MessAuswertung.cs`, `MessungBelegTests.cs`, `MessungEinheitTests.cs`, Ziel in `tools/mk/vergleichslauf-d.mk`). Die .NET-Belegtests über die Ablage liefen grün in `make gates` (171 bestanden). Nicht von mir wiederholt: die Aufzeichnung am echten Server (EULA, nur lokal). |
| 2 Spezifikation und Bau | erfüllt | `SPEC-030` führt die Antwortform mit Messung und Setzung getrennt (Zeile 2001), `SPEC-011`/`SPEC-013` gezogen (Zeilen 1602 bis 1604); die Zuordnung des Mehrfach-RPC zur Seite ERROR und DONE steht nicht mehr in der Spezifikation. Bau: Parser (`RpcParser`, `RpcParameterReader`, `RpcRequest`, `ByteReader`), Dispatch (`RequestLoop`, `RpcBinder`, `RpcParameters`), Antwort (`ResponseSequencer`, `ResponseRun`, `RpcTrailer`) im Diff; `clean-build` und Stufe 6 grün. Neue Tests: `RpcCallSequenceTest`, `RpcSequenceDispatchTest`, `RpcSequenceAbortTest` bestanden im `clean-build`. |
| 3 Nachweis | erfüllt | Der Test `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` ist im Bestand nicht mehr vorhanden (Suche über das ganze Repo: Treffer nur in Plan, [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md)/0051, alten Reviews); ersetzt durch `Mehrere_RPCs_in_einer_Nachricht_gelingen_mit_UpdateBatchSize_2`, Begründung in `fec43d39`. Lauf: siehe oben, bestanden. Matrix und `make e2e` stehen auf dem neuen Stand, bis auf die „Erwartung“-Texte (oben). |
| Jede Aussage an Test, Mitschnitt oder als Festlegung gekennzeichnet | erfüllt mit Rest | `SPEC-030` und [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) trennen Messung (W-10 bis W-13) und Setzung (Grenzen 4096/1024, `0x80`, `sp_prepexec`/`sp_execute`, Port-Form, Fehler, die die Verbindung schließen, Abbruch und Frist). Rest: siehe V-2, V-3. |
| `make gates` grün | erfüllt | Exit 0 (oben); Hinweis: Klassen `werkzeug`/`stempel` mit Beleg übersprungen, `tests` und `immer` gelaufen. |
| Review-Report liegt vor | erfüllt | zwei Reports unter `docs/reviews/`; F-1 (Folge-ADR) durch [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) beantwortet, F-3 bis F-7, F-9, F-13 laut Re-Review behoben; F-8 bleibt LOW, F-11/F-12 unverändert (nicht neu geprüft). |
| Doku-Update (öffentlicher Vertrag) | erfüllt | `docs/user/einbettung-und-betrieb.md` und `docs/user/referenz-konfiguration.md` (je 1 Zeile) und KDoc von `QueryRequest`/`QueryBackend.execute` nennen die wiederholte `requestId`; [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 8 deckt die Form (keine Änderung der Signatur, daher kein Eintrag in `SPEC-059` nötig). Inhaltlich nicht neu geprüft über das Gelesene hinaus. |
| Closure-Notiz mit Lerneintrag | offen | Plan §7 trägt „Wird bei der Closure gefüllt.“ (Auftrag: nicht schließen). |
| Beobachtungs-Register fortgeschrieben | offen | im Diff keine Datei unter `docs/plan/planning/observations/`; §7 nennt keine Antwort („keine Beobachtung angefallen“ oder ein Eintrag). |
| Jedes Risiko aus §6 trägt einen Ausgang | offen | beide Risiken (Antwortform ungemessen, Port-Form) tragen noch „offen bis zur Closure“. Der Befund trägt beide Ausgänge: Antwortform ist gemessen (W-10 bis W-13, mit den benannten Grenzen), Port-Form ist per [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 8 als Setzung entschieden. |
| Drei Paarungen (Anker, Folge-Slice, Register) | offen | an §7 gebunden, noch nicht gefüllt. |

## Entscheidungs-Konformität

- [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidungen 1, 2, 5, 6, 8, 10: am Code und an Tests gesehen (Trennbyte `0xFF`, `MORE|0x80` je Aufruf außer dem letzten, Grenzen je Nachricht, `0x80` trennt nicht, Port, Test der Ablehnung ersetzt). Stufe 6 belegt am Serverprozess nur den Fall zwei Aufrufe plus einer (drei Zeilen), nicht die Grenzen.
- Entscheidungen 3, 4, 7, 9: von den Unit- und Dispatch-Tests getragen (nicht von mir einzeln mutiert); am echten Server nur die Entscheidung 3 für den mittleren Aufruf gemessen (W-13), der Rest ist als Setzung benannt.
- [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) im Diff unverändert (`AGENTS.md` §3.5 eingehalten); Indexzeile vermerkt die teilweise Ersetzung (`docs/plan/adr/README.md`).
- [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md): Katalog unverändert; `sp_prepexec` und `sp_execute` in einer Mehrfach-Nachricht werden abgelehnt (Setzung), ihr Einzelverhalten bleibt.

## Findings

| ID | Klasse | Befund | Stelle |
|---|---|---|---|
| V-1 | MEDIUM (Plan, DoD) | Vier DoD-Nachweispflichten sind offen (Closure-Notiz, Register, Risiko-Ausgänge, Paarungen); alle hängen an der Closure, nicht am Code. Kein Fehler des Standes, aber `done/` ist erst nach ihnen zulässig. | Plan §2, §6, §7 |
| V-2 | LOW (Widerspruch in `Accepted`-ADR) | [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) trägt trotz `Status: Accepted` noch die Zustandssätze der Entwurfszeit: „Annahme durch den Nutzer steht aus“, „Bis zur Annahme bleibt Befund F-1 offen“, „Bis dahin steht [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) unverändert … Befund F-1 bleibt offen“. Eine angenommene ADR ist nicht zu überschreiben (`AGENTS.md` §3.5); Korrektur nur als neue Entscheidung oder durch den Nutzer entschiedene redaktionelle Änderung. Der Planner entscheidet. | [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Kopf von „Entscheidung“, „Empfehlung des Architects“ 4, „Konsequenzen“ |
| V-3 | LOW (Aussage ≤ Beleg) | Die Matrix führt `Mehrere_RPCs_…_UpdateBatchSize_2` und die Zeile in Abschnitt 7 mit „Stand: 2026-10-31“; nach der Aktualisierung (oben) ist der Stand-Satz des Abschnitts für die Zeile falsch. Der Re-Review N-2 (KDoc des Fehlertests nennt W-2 und W-13 breiter als die Eingabe) ist laut Commit `4c7b069b` auf „W-2“ gezogen; ich habe das nicht neu gelesen. | `docs/plan/kompatibilitaetsmatrix.md` Abschnitt 7 |
| V-4 | INFO (Stufe 6) | Der Test beweist nur: drei Zeilen, Gruppen zu 2 und 1, `INSERT`/`UPDATE` ohne Ergebnismenge, `Update` meldet 3, Verbindung `Open`, kein Protokollfehler im Log. Er prüft keine Fehlerfälle, keine Ergebnismengen, keine Ausgabewerte am Serverprozess; die tragen nur die Dispatch-Tests am Skript-Backend. | `LueckenTests.cs` |

## Ungemessen

- Fehler im ersten oder letzten Aufruf einer Nachricht (am echten Server und am Referenzclient); gemessen ist der Fehler im mittleren von drei Aufrufen (W-13, ein Szenario je Instanz).
- Fehler vor dem ersten Ergebnis eines nicht letzten Aufrufs (Setzung in [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) Entscheidung 3).
- Aufrufe mit Ergebnismengen in einer Nachricht (Messprotokoll: nicht gemessen; Matrix: „offen“); Stufe 6 hat keine.
- `sp_prepexec` und `sp_execute` in einer Nachricht am Referenzclient (am Server dieser Bibliothek abgelehnt, am echten Server nicht gemessen); `UpdateBatchSize` mit vorbereiteten Befehlen nicht gefahren.
- Trennbyte `0x80`; mehr als drei Aufrufe je Nachricht am echten Server; die Grenzen 4096 Parameter je Nachricht und 1024 Aufrufe am echten Server.
- Abbruch (ATTENTION) und Frist mitten in der Nachricht: nur am Server dieser Bibliothek durch Tests am Skript-Backend belegt, nicht am Serverprozess und nicht am echten Server.
- Ein anderer Client als `Microsoft.Data.SqlClient` 7.1.1 (mssql-jdbc, ODBC, FreeTDS, `sqlcmd`) und andere Versionen des Referenzclients; Stufe 6 ist ein Lauf, ein Host.
- `ContinueUpdateOnError`.
- Mutationen je Zusage (nicht Teil des Verifier-Zuschnitts; die Mutation zu F-3 hat der Re-Review gefahren, die zu F-5 nicht).
- Der Flake von `ServerSmokeTest`, den der Re-Review als N-4 führt, trat in `clean-build`, `e2e` Stufe 1 und `gates` nicht auf; damit weder reproduziert noch widerlegt.
