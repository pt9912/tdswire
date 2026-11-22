# Verifikationsbericht: slice-simulation-anweisungsformen-erweitern — 2026-11-22

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, nichts nach außen.

**Stand:** Branch `slice-simulation-anweisungsformen-erweitern`, Spitze `bf6ffdab` (Re-Review; der Code-Stand ist seit `0917eee2`/`18fcca07`/`6d002217`/`34e7be7e` unverändert, danach nur Berichte); Arbeitsbaum sauber vor und nach allen Läufen. `main` steht auf `4f6af8d7` (seit der Basis `a822f623` nur eine Zeile der Roadmap); `git merge-tree` meldet für `main` und die Spitze keinen Konflikt. Alle Läufe unten gelten für `bf6ffdab`.

**Gegenstand:** Slice [slice-simulation-anweisungsformen-erweitern](../plan/planning/in-progress/slice-simulation-anweisungsformen-erweitern.md); [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität); [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) (`Accepted` 2026-11-21), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11, [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md); [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-045](../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig); Messprotokoll [referenzabgleich.md](../messungen/referenzabgleich.md), Mutationsbeleg [mutationen-anweisungsformen-2026-11-21.md](../messungen/mutationen-anweisungsformen-2026-11-21.md); Reviews [Review](2026-11-21-slice-simulation-anweisungsformen-erweitern.md) (0 HIGH, 3 MEDIUM, 3 LOW, 5 INFO) und [Re-Review](2026-11-21-slice-simulation-anweisungsformen-erweitern-re-review.md) (0/0/0, 1 INFO).

## Urteil

**DoD nicht erfüllt, ein Punkt offen und trivial behebbar: `make gates` endet mit Exit 2 und `make docs-check` ist rot** (ein Befund, F-1: eine nicht verlinkte Kennung im Re-Review-Bericht `bf6ffdab`). Alle übrigen Gates sind grün, und alle Messläufe tragen die Zusagen des Slice: `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d LAUF=mitschnitt` und `LAUF=live` sind Exit 0, die vorhergesagten Server-Spalten treffen **zeichengleich** zu. Nach Behebung von F-1 (ein Link, kein Code) und der Nachführung der Sätze „Lauf steht aus“ (F-2) ist der Slice aus Sicht der Verifikation schließbar. Die Closure-Punkte (Notiz, Register, Risiko-Ausgänge, Paarungen) sind erwartungsgemäß noch offen und Sache des Planners.

## Selbst gefahrene Sensoren (End-Stand `bf6ffdab`, Docker, unter der Messsperre, keine künstliche Last)

Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` von mir angelegt (Kennung im `owner`), nach dem letzten Lauf freigegeben; `available` vor den Läufen 13,1 bis 15,3 GiB, nie unter 4000 MB; Lastmittel des Hosts zu Beginn 20, danach 6 bis 10 durch fremde Prozesse (root, `go test`), nicht durch mich.

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0 (Jar, Tests, detekt, `--no-cache`); 1558 Zeilen `PASSED`, keine `FAILED`-Zeile eines Tests (die neun Zeilen mit „FAILED“ sind Namen von Testfällen mit Status `PASSED`, zum Beispiel `ResponseFailureTest > … FAILED_CLOSE_CONNECTION`); alle Gradle-Stages `BUILD SUCCESSFUL`; die Klassen des Slice (`WaitFormsTest`, `SimulationBackendWaitTest`, `SimulationBackendDisjunctionTest`, `WaitForWireTest`, `FixedFormsTest`, `StatementFormsTest`) stehen als `PASSED` | 609 s |
| `make gates FORCE=1` | **Exit 2**: `docs-check` rot, 1 Befund (F-1); davor `baseline-verify`, die vier Selbsttests der Stempel grün | 30 s |
| `make -k gates FORCE=1` (zur Diagnose, damit die übrigen Gates laufen) | Exit 2, **nur** `docs-check` rot; grün: `detekt-selbsttest`, `kopplung-zahlen`, `skript-selbsttest`, `detekt`, `build-jar`, `referenztest-dotnet`, `test`, `flaeche`, `gate-zuordnung` (16 Gates, Selbsttest 33 Fälle), `stempel-eingaenge`, vier Selbsttests der Stempel (118, 95, 102, 67 Fälle); die Stage `test` kam aus dem Cache des eben gelaufenen `clean-build` | 35 s |
| `make docs-check` (einzeln) | Exit 2, `1741 Datei(en) geprüft, 1 Befund(e)`: `docs/reviews/2026-11-21-slice-simulation-anweisungsformen-erweitern-re-review.md:44 [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) id-unlinked` | 3 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 442 s)“; Stufen 1 bis 9: 71, 98, 60, 47, 15, 16, 35, 19, 81 s, alle grün | 442 s |
| `make kompatibilitaet` (Standard `KATEGORIE=Szenario`) | Exit 0, 14 von 14 Tests bestanden, darunter `Anweisung_ausserhalb_der_erkannten_Formen_ist_ein_Anfragefehler_…` mit `SELECT 1 + 1` (im Log des Servers `sql=[SELECT 1 + 1]`); `SynchronitaetTests` (Stufe 7, mit `SELECT 1 + 1`) 10 von 10 in `make e2e` | 15 s |
| `make vergleichslauf-d LAUF=mitschnitt` | Exit 0, grün, beide Teilläufe (je 324 Schritte); 2022: 18 gelistete Abweichungen, 2025: 8 | 36 s |
| `make vergleichslauf-d LAUF=live` | Exit 0, grün, beide Teilläufe (je 25 Schritte) | 134 s |

Nicht gefahren: `make spec-abdeckung` (Sache des Implementers, der Zähler ist im Re-Review gegengelesen), die Mutationen des Beleges (Implementer; der Re-Review hat R2, M22, M23, M25, R5 nachgefahren, ich habe sie nicht wiederholt), `make flaeche` und `make kopplung-zahlen` einzeln (beide liefen grün in `make -k gates`). Die Stempel (`git notes`, lokal) für `bf6ffdab` hat mein erster `make gates`-Lauf geschrieben; sie sind kein Beleg für `docs-check` (Klasse `immer`).

Gesamt rund 24 Minuten Wanduhr (Start 03:16, Ende 03:40) bis zum letzten Lauf, davon rund 21 Minuten aktive Docker-Läufe. Größte Posten: `make clean-build` 609 s, `make e2e` 442 s (Stufe 2 mit 98 s, Stufe 9 mit 81 s, Stufe 1 mit 71 s), Live-Lauf 134 s (davon Bau und Start je Instanz 44 und 47 s).

## Zu den sechs besonderen Fragen

### (1) Vorhergesagte Server-Spalten: bestätigt

**Mitschnitt, vier Szenarien `WAITFOR`** (`timeout-wartend`, `cancel-wartend`, `cancel-kurz-vor-timeout`, `cancel-kurz-nach-timeout`, je Feld `…/01/`). Rohzeilen des Laufs (identisch in allen vier Szenarien; Beispiel `timeout-wartend`):

```
mitschnitt-2022  referenz tokenfolge  SESSIONSTATE DONE || DONE
mitschnitt-2022  server   tokenfolge  DONE || DONE            -> ok Ausnahme (Luecke)  (Grund A-18)
mitschnitt-2025  referenz tokenfolge  DONE || DONE
mitschnitt-2025  server   tokenfolge  DONE || DONE            -> ok gleich
beide Instanzen  done      DONE Status ERROR, Befehl 0x00fd, Zeilen - ; DONE Status ATTN, Befehl 0x00fd, Zeilen -   (Referenz = Server)
beide Instanzen  fehler    -  (Referenz = Server)              -> ok gleich
beide Instanzen  nachrichten 2, attention-gesendet 1, attention-quittiert 1 (Referenz = Server) -> ok gleich
```

Die Server-Spalte `DONE || DONE` der vier Regeln `…/01/tokenfolge@2022` trifft zeichengleich zu (der Lauf vergleicht die Ausnahme wortgleich und ist grün). Die Regeln `…/01/fehler` und `…/01/tokenfolge@2025` der vier Szenarien (acht Regeln) sind aus der Liste entfernt, und die Felder stehen im Lauf als `gleich`. Der Server beantwortet das ATTENTION im Warten mit dem Paar aus `DONE[ERROR]` (`0x00fd`) und `DONE[ATTN]`, wie die Referenz.

**Live, die zwei Schritte** (Rohzeilen, beide Teilläufe 2022 und 2025 gleich):

```
live: referenz anweisung-ohne-tabelle  spalten=[:int/Int32] zeilen=1 [(2)]
live: server   anweisung-ohne-tabelle  spalten=[:int/Int32] zeilen=1 [(2)]      -> ok gleich
live: referenz bedingung-mit-or        spalten=[Id:int/Int32] zeilen=2 [(1);(2)]
live: server   bedingung-mit-or        spalten=[Id:int/Int32] zeilen=2 [(1);(2)]  -> ok gleich
```

Beide Vorhersagen aus dem Messprotokoll (Abschnitt „Anweisungsformen erweitert“) treffen zu. Neu gemessen ist dabei mehr, als der Slice behauptet: für beide Instanzen (je ein Lauf) liegen jetzt Spaltentyp, Spaltenname und Werte der Referenz vor; bisher stand dort „nur: keine Ausnahme“ und „welche Zeilen kommen, ist nicht gelesen“ (siehe F-4).

### (2) Entfallene Regeln: entbehrlich

Gegen `main` fehlen in `live-ausnahmen.tsv` genau `anweisung-ohne-tabelle` und `bedingung-mit-or` (A-4, A-5) und in `mitschnitt-ausnahmen.tsv` genau die acht Regeln `fehler` und `tokenfolge@2025` der vier `WAITFOR`-Szenarien (A-15); `diff` der Kennungen: nichts sonst. Beide Läufe sind ohne diese Regeln grün; ein Feld ohne Regel ist im Lauf `gleich` (Rohzeilen oben), eine überflüssige Regel würde den Lauf rot machen („Ausnahme entfaellt“). Die Liste führt 18 Regeln (Mitschnitt) und 7 (Live).

### (3) A-17: je Szenario eigener Grund, wahr

`batch-statement-abbrechend` (fünf Regeln; Text `SELECT 1/0; SELECT 2` laut `MitschnittLaufTests.cs`): Grund „die erste Anweisung enthält einen Ausdruck, die Division durch null“. Wahr: der Text steht so im Test, der Server antwortet `ERROR DONE` (50001 `Unsupported SQL.`), die Ausnahmen laufen wortgleich in beiden Teilläufen (`ok Ausnahme (so gewollt)`). `batch-batch-abbrechend` (`…/01/fehler`; Text `SELECT Id FROM gibtesnicht; SELECT 2`): Grund „Folge aus einer Auswahl und einer Konstantenauswahl, kein Ausdruck; Form S2 sind genau zwei Auswahlen der Form S1, `SELECT <Ganzzahl>` gehört zu keiner Folge“. Wahr, im Lauf wortgleich bestätigt (Referenz 208, Server 50001). Die beiden Gründe sind nicht gegeneinander ausgetauscht. Die Art steht auf *so gewollt* ([ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 6). Grenze: beim ersten Szenario ist die Division nicht der einzige mögliche Grund (auch dort stünde `SELECT 2` in keiner Folge); der Grund sagt nur, was wahr ist, nicht, dass es der einzige ist (F-3, INFO).

### (4) SPEC-029 und Änderungsprotokoll: was der Lauf trägt

Wortlaut SPEC-029: die vier Szenarien „sollen den Abbruch eines wartenden Befehls am Server abspielen; belegt ist das Paar durch den Test mit gesteuerter Uhr, die Läufe … `LAUF=mitschnitt` und `LAUF=live` gegen den Server sind nicht gelaufen, die Server-Spalten der verkleinerten Regeln … sind aus dem Bau vorhergesagt“.

| Aussage | Beleg jetzt |
|---|---|
| Die vier Szenarien spielen den Abbruch eines wartenden Befehls am Server ab (Paar `DONE[ERROR]` `0x00fd`, `DONE[ATTN]`, zwei Nachrichten, ATTENTION quittiert) | **vom Lauf getragen**: `LAUF=mitschnitt` grün, beide Instanzen, je ein Lauf; Felder `nachrichten`, `attention-*`, `done`, `fehler` gleich, `tokenfolge` gleich bei 2025, bei 2022 nur SESSIONSTATE (A-18) |
| Server-Spalten der verkleinerten Regeln (`DONE \|\| DONE`) | **vom Lauf getragen** (zeichengleich) |
| `SELECT 2` und `Id = 1 OR Id = 2` am Server gleich der Referenz (Spaltentyp, Werte) | **vom Live-Lauf getragen**, je ein Lauf je Instanz |
| `make kompatibilitaet`: `SELECT 1 + 1` als Anweisung außerhalb der Formen (Matrix: „am Referenzclient noch nicht gemessen“) | **vom Lauf getragen**: `Szenario` 14 von 14, Stufe 7 in `make e2e` grün |
| `QUERY_TIMEOUT` kürzer als die Wartezeit endet mit `TDS_TIMEOUT`; reguläres Ende der Wartezeit (Änderungszähler 0); Bereich von `hh:mm:ss`, `00:00:00`; `OR` mit `IN`, `IS [NOT] NULL`, mehr als zwei Terme, `NULL` in der Folge; `SELECT <Ganzzahl>` für andere Werte als 2, Grenzen von `Int32`; `SELECT 01`, `SELECT -0`; `SET FMTONLY ON SELECT 2` | **vom Lauf nicht getragen**: nur durch Tests des Servers (`WaitForWireTest`, `SimulationBackendWaitTest`, …) belegt, am echten Server ungemessen; die Texte führen sie als Setzung (siehe (6)) |

Folgerung: Die Sätze „die Läufe sind nicht gelaufen“ und „belegt erst der Lauf, der aussteht“ (SPEC-029, Änderungsprotokoll 2026-11-21, Messprotokoll Abschnitt „Anweisungsformen erweitert“ und Zeile A-15, Matrix Nr. 12/„Fehler außerhalb der Formen“) sind **nach diesem Lauf überholt**, nicht falsch (F-2). Die Aussage „sollen … abspielen“ darf auf Indikativ „spielen … ab“ gehoben werden, nur für die belegten Zeilen der Tabelle.

### (5) Reproduzierbarkeit der Live-Läufe (A-7)

In diesem einen `LAUF=live` (zwei Teilläufe) zeigte die Referenz beider Instanzen den Zusatz „ Operation cancelled by user.“ **nicht**, der Server **beide Male**; `Number 0, Class 11, State 0`, `Open` und Folgeabfrage gleich; der Lauf blieb grün (`ok Ausnahme (Luecke) cancel-mit-folgeabfrage`). Das entspricht der Regel der Liste und ist nicht dem Slice anzulasten. Der Fall „Referenz mit Zusatz, Lauf rot“ (1 von 8 Teilläufen im Verifikationsbericht vom 2026-11-20) trat bei diesen zwei Teilläufen nicht ein; die Häufigkeit ändert sich damit nicht belastbar, ein Lauf ist keine Rate. Ein roter Live-Lauf allein an diesem Schritt bleibt kein Befund des Servers (Folge-Slice `cancel-zusatztext-ursache-untersuchen`).

### (6) Setzungen sind als Setzung gekennzeichnet

Gelesen in `spec/spezifikation.md` (SPEC-036, Form C, Form Z, Abschnitt „Term“, Änderungsprotokoll), `docs/messungen/referenzabgleich.md` (Abschnitt „Anweisungsformen erweitert“), `docs/user/referenz-konfiguration.md` und im KDoc von `metadataOnly`: `SELECT 01` und `SELECT -0` (Werte 1 und 0), die Untergrenze `-2147483648` als `Int32` („nach Kenntnis in T-SQL kein `int`-Literal, gemessen ist das nicht“) und die Erkennung hinter `SET FMTONLY ON` stehen unter „**Ungemessene Setzungen** (Entscheidung des Nutzers 2026-11-22; am Server nicht gemessen)“; der Bereich von `hh:mm:ss`, das reguläre Ende der Wartezeit und `TDS_TIMEOUT` stehen als „Setzung, nicht gemessen“; die Grenzen von `OR` (Terme, Zahl, `NULL`) als Setzung beziehungsweise „abgeleitet, nicht gemessen“. Keine dieser Aussagen steht als gemessen; kein Verstoß gegen [AGENTS.md](../../AGENTS.md) §3.8. Der Live-Lauf widerspricht keiner Setzung (er berührt keine davon).

## Entscheidungs-Konformität

| Entscheidung | Befund |
|---|---|
| **[ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Nr. 1** (Lesart „abschließend“ verworfen; Liste wächst nur durch Spezifikation) | **Erfüllt.** SPEC-036 „Nicht erkannt“ und `TDS-FA-11.a` Schritt 2 sagen es so; `NOT`, `<`, `>`, `LIKE`, `BETWEEN`, `<>`, `!=`, `NOT IN` bleiben abgewiesen mit Test je Form (`StatementFormsTest`), Begründung „nicht gebaut und nicht gemessen“ |
| **Nr. 2** (Grenze Ausdruck) | **Erfüllt.** Negativtests `SELECT 1 + 1`, `1 / 0`, `1, 2`, `2 AS x`, `'a'`, `NULL`, `1.5`, `@p`, `@@VERSION`, `2147483648` (`FixedFormsTest`, `SimulationBackendFixedTest`); der Kompatibilitätstest benutzt `SELECT 1 + 1` |
| **Nr. 3** (`OR` in S1, reine Folge, `SimQuery.filter` `null`; kein `OR` in `UPDATE`/`DELETE`, keine Mischung, keine Klammer, kein Konstantenterm) | **Erfüllt.** Negativtests `UPDATE … OR`, `DELETE … OR`, Mischung, Klammer in `StatementFormsTest`; Auswertung und Vorfilter in `SimulationBackendDisjunctionTest`; `RowFilter` und öffentliche Fläche unberührt (`make flaeche` grün). Live: `OR` am Referenzclient gleich der Referenz |
| **Nr. 4** (`SELECT <Ganzzahl>`, `Int32`, Antwort wie `SELECT 1`) | **Erfüllt**, Live gleich (`[:int/Int32] [(2)]`) |
| **Nr. 5** (`WAITFOR DELAY`, ATTENTION im Paar, `QUERY_TIMEOUT` → `TDS_TIMEOUT`, Ende ohne Abbruch, Beenden) | **Erfüllt.** `WaitFormsTest` (`'00:00:00'`, `'24:00:00'`, `@d`, `@@VERSION`), `SimulationBackendWaitTest` (gesteuerte Uhr, Austragung der Registrierung, R2, M22 bis M25 rot), `WaitForWireTest` (Handvektor, kein Warten der echten Zeit); Mitschnitt gleich (oben). `QUERY_TIMEOUT`, Ende ohne Abbruch bleiben Setzung |
| **Nr. 6** (A-17 so gewollt, je Szenario eigener Grund) | **Erfüllt** ((3)); Statusbits `DONE[MORE\|ERROR]` am Server unabgespielt, durch `ResponseMeasuredTest` belegt |
| **Nr. 7** (Lastenheft unverändert) | **Erfüllt** (`spec/lastenheft.md` nicht im Diff) |
| **[ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14/Offen 12(a)** | **Erfüllt.** Beide Dateien und [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) im Diff unverändert (`git log`: letzte Änderung vor dem Zweig); nur der ADR-Index (zwei Vermerke „teilweise ersetzt“) und die neue [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md)-Datei stehen im Diff; [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) selbst seit `5bc2891f` (`Accepted`) unverändert |
| **[ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md)** (Paar bei wartendem Befehl: `0x00fd`) | **Erfüllt**, jetzt auch am Server abgespielt (Mitschnitt, vier Szenarien) |
| Hard Rules §3.1, §3.2, §3.5, §3.6 | kein Host-Aufruf von `gradle` oder `java`; im Diff kein `@Suppress`, `noqa`, `nolint` (Treffer nur im Text der Reviews); `config/`, `Dockerfile`, `Makefile`, `tools/` unberührt; keine `Accepted`-ADR geändert; keine Schwelle gesenkt |

## DoD Punkt für Punkt

Keine Haken gesetzt; `[x]` ist Sache des Planners bei der Closure.

| DoD-Punkt (§2) | Befund |
|---|---|
| **`OR` und `SELECT <Ganzzahl>` (A-5, A-4)** | **Erfüllt.** Test je Form (Happy, Grenze, Negativ) grün in `clean-build`; die geforderten Negativlisten stehen in den Tests (Stichprobe gelesen: `OR` in `UPDATE`/`DELETE`, Mischung, `LIKE`, `BETWEEN`, `<>`, `!=`, `NOT IN`; `2147483648`, `1 + 1`, `1 / 0`, `1, 2`, `2 AS x`, `'a'`, `NULL`, `1.5`, `@p`, `@@VERSION`); die zwei Live-Regeln entfallen und beide Schritte lesen Spaltentyp und Werte (`LiveSzenarioTests.cs`, Rohzeilen oben) |
| **`WAITFOR DELAY` mit Abbruch (A-15)** | **Erfüllt.** Paar im Warten, `TDS_TIMEOUT`, Ende ohne Abbruch (Setzung) in `WaitForWireTest`/`SimulationBackendWaitTest`, die Tests warten die Zeit nicht ab; acht Regeln entfallen, `tokenfolge@2022` bleibt mit Grund A-18, `…/02/tokenfolge@2022` bleiben; der Mitschnitt-Lauf belegt es am Server |
| **Regeln von A-17 mit je eigenem Grund (kein Bau)** | **Erfüllt**, in der Liste und im Messprotokoll (Zeile A-17) wahr, Selbsttest der Liste in `make gates` grün ((3)) |
| `make gates` Exit 0 und Sensoren je Art | **Nicht erfüllt:** `make gates` Exit 2, `make docs-check` Exit 2 (F-1). Alle anderen Gates und `flaeche`, `kopplung-zahlen` grün; `clean-build`, `e2e`, `kompatibilitaet` und beide Läufe von `vergleichslauf-d` grün |
| Review durchgeführt, Report unter `docs/reviews/` | **Erfüllt:** Review und Re-Review liegen vor, F-1 bis F-3, F-5, F-6, F-9 behoben, F-4 und F-7 durch Nutzerentscheid (2026-11-22) als Setzung benannt (Commit `34e7be7e`, im Re-Review nicht bewertet; von mir gelesen, siehe (6)); offen sind INFO-Befunde ohne Aktion |
| Doku-Update (SPEC-036, SPEC-045, Matrix, Messprotokoll, beide `.tsv`, `TDS-FA-11.a`, SPEC-029, `einbettung-und-betrieb.md`) | **Erfüllt**, mit der Nachführung F-2 (Sätze „Lauf steht aus“). SPEC-045 trägt den Satz, dass ein Schritt ohne Ausnahme Spaltentyp und Werte vergleicht; `einbettung-und-betrieb.md` nennt `SELECT <Ganzzahl>`, `WAITFOR DELAY` und die `OR`-Folge |
| Closure-Notiz mit Steering-Loop-Lerneintrag | **Offen:** §7 steht auf „Wird bei der Closure gefüllt.“ |
| Beobachtungs-Register fortgeschrieben (drei Beobachtungen) | **Teilweise:** je ein Beleg unter `evidence/` und `state.md` der drei Beobachtungen im Diff; die Schließung ist Closure-Arbeit |
| Jedes Risiko aus §6 trägt einen Ausgang | **Offen bis zur Closure**, ein Risiko (Setzungen der Form C) trägt „entschieden“. Vorschläge nach Belegen: (1) Grenze zur „SQL-Engine“: **entfallen**, der Bau bleibt auf Form Z beschränkt (Mitschnitt und Tests grün); (2) je Szenario ein Lauf: **weiter offen** (Rohzeilen oben, ein Lauf je Instanz); (3) Regel mit zwei Gründen: **entfallen** (Regeln auf A-18 verkleinert, Lauf grün); (4) Clients (d-migrate, mssql-jdbc) bekommen Verhalten, das bisher Abweisung war: **weiter offen** für andere Clients als den Referenzclient (Negativtests grün, kein Client-Lauf); (5) Größe: **entfallen** (drei Liefer-Punkte) |
| Die drei Paarungen (Anker · Folge-Slice · Register) | **Offen bis zur Closure:** `liegt in`-Zeile fehlt; der Folge-Slice `cancel-zusatztext-ursache-untersuchen` ist Sache des Planners |

Der Slice hat drei Liefer-Punkte (Rückführungs-Trigger „mehr als drei“ nicht erfüllt).

## Plan-vs-Code-Diff (§3, `git diff main...HEAD`, 46 Dateien, 1767 Einfügungen, 165 Löschungen)

Dateiweiser Abgleich der Dateien (ohne `docs/reviews/`) gegen die Tabelle in §3 per Skript (Scratchpad, nicht im Repo): jede Quell-, Test-, Spec-, Doku- und Messdatei steht dort; nicht in der Tabelle, aber im Diff: der Slice selbst und die drei Belege unter `observations/BEO-TDS/…/evidence/` (die Tabelle nennt das Verzeichnis). Umgekehrt steht keine Datei als `update` oder `create` in der Tabelle, die der Diff nicht berührt; `SqlScanner.kt`, `AttentionCancelTest.kt`, `CancelTimeoutTest.kt` und `MitschnittLaufTests.cs` stehen nur im Satz „bleiben unverändert“. Der Satz unter der Tabelle („Abgleich gegen den Diff ist gelaufen“) stimmt. Neue Produktionsdateien: `WaitForms.kt`, `WaitExecution.kt`; geändert: neun weitere unter `simulation/`, alle im Plan. Öffentliche Fläche: `make flaeche` grün (Typnamen), der öffentliche Konstruktor `SimulationBackend(catalog, onStructure = null)` ist im Änderungsprotokoll genannt.

## Findings

| ID | Schwere | Befund | Quelle | Pfad |
|---|---|---|---|---|
| F-1 | MEDIUM | `make docs-check` und damit `make gates` sind auf `bf6ffdab` rot: `docs/reviews/2026-11-21-slice-simulation-anweisungsformen-erweitern-re-review.md:44 [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) id-unlinked` (die Kennung steht dort in der Wiedergabe von F-5 ohne Link). Die Datei kommt mit dem Commit `bf6ffdab` (Re-Review) ins Repo; `docs-check` gehört zur Klasse `immer` und trägt keinen Stempel, ein früherer grüner Lauf liegt mir nicht vor. Die DoD verlangt `make gates` Exit 0 und `make docs-check` Exit 0 (§2, §5). Behebung durch Implementer oder Reviewer: Kennung mit Link oder in Backticks setzen; danach `make docs-check` und `make gates` wiederholen. | Slice §2 und §5; `harness/README.md` §Sensors | `docs/reviews/2026-11-21-slice-simulation-anweisungsformen-erweitern-re-review.md` Zeile 44 |
| F-2 | LOW | Sätze, die den Lauf als ausstehend führen, sind nach diesem Bericht überholt: SPEC-029 (Satz „… sind nicht gelaufen … aus dem Bau vorhergesagt“), Änderungsprotokoll 2026-11-21 („belegt erst der Lauf …, der aussteht“), Messprotokoll Abschnitt „Anweisungsformen erweitert“ („**Nicht gelaufen** sind …“; „Erwartet sind …“) und Zeile A-15 („ein Lauf von `LAUF=mitschnitt` belegt sie“), Matrix („mit `SELECT 1 + 1` am Referenzclient noch nicht gemessen“, zwei Zeilen). Sie sind nicht falsch, aber veraltet; die Messzeilen oben (Mitschnitt und Live) gehören als Zusatz in den Abschnitt des Messprotokolls (Planner oder Implementer; ich habe das Messprotokoll nicht angefasst, weil §2 keine Rohzeilen des Verifiers verlangt). | [AGENTS.md](../../AGENTS.md) §3.8 (Aussage trägt so weit wie der Beleg, hier: weniger als der Beleg); Slice §2 Doku-Update | `spec/spezifikation.md` (SPEC-029, Änderungsprotokoll); `docs/messungen/referenzabgleich.md`; `docs/plan/kompatibilitaetsmatrix.md` |
| F-3 | INFO | Der Grund der fünf Regeln `batch-statement-abbrechend/01/*` nennt die Division als Ursache; sie ist wahr, aber nicht die einzige: auch `SELECT 2` als zweite Anweisung gehört nach [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) Entscheidung 4 und 6 zu keiner Folge. Keine Aktion nötig, solange niemand die Division allein als Hindernis liest. | [AGENTS.md](../../AGENTS.md) §3.8 | `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` |
| F-4 | INFO | Die Aussagen „gemessen ist nur der Erfolg“ (`OR`) und „nur: gelingt“ (`SELECT 2`) in SPEC-036, Form C, und im Messprotokoll untertreiben jetzt den Beleg: der Live-Lauf liest für beide Instanzen Spaltentyp, Namen und Werte der Referenz (Rohzeilen oben, je ein Lauf). [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) bleibt als `Accepted` unverändert. | [AGENTS.md](../../AGENTS.md) §3.8 | `spec/spezifikation.md` SPEC-036 Form C, Absatz „Term“ |
| F-5 | INFO | Die Referenz und der Server zeigen im Live-Lauf beim Schritt `cancel-mit-folgeabfrage` den bekannten Unterschied (Zusatz „ Operation cancelled by user.“ nur am Server, A-7); er ist gelistet, der Lauf grün; kein Befund des Slice. | Slice §1 (Abgrenzung), Folge-Slice `cancel-zusatztext-ursache-untersuchen` | `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` |

| Schwere | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 (F-1) |
| LOW | 1 (F-2) |
| INFO | 3 (F-3, F-4, F-5) |

## Empfehlung

**Closure: ja, nach Behebung von F-1 und Nachführung von F-2 durch Implementer und Planner.** Die Zusagen der DoD sind am Code und an beiden Referenzinstanzen belegt (Frischbau 1558 Zeilen `PASSED`, `e2e` 9 von 9, `kompatibilitaet` 14 von 14, Mitschnitt und Live grün, die Server-Spalten der vorhergesagten Regeln zeichengleich), die Entscheidung [ADR-0058](../plan/adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) ist umgesetzt, Setzungen sind als Setzung gekennzeichnet, und keine Aussage des Slice ist breiter als der Beleg. Einzige Bedingung: `make gates` und `make docs-check` müssen Exit 0 liefern (F-1; ein Link in einem Bericht, kein Code, kein erneuter Messlauf nötig, da sich der Quelltext nicht ändert). Nach dem Commit dieses Berichts ist `make docs-check` wegen F-1 weiter rot; dieser Bericht fügt keinen eigenen Befund hinzu.

Rohausgaben der Läufe liegen im Scratchpad des Verifiers (`clean-build.log`, `gates.log`, `gates-k.log`, `e2e.log`, `kompat.log`, `mitschnitt.log`, `live.log`), nicht im Repo.
