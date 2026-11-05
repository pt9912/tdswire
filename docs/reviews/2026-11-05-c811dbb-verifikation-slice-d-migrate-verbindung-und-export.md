# Verifikationsbericht: slice-d-migrate-verbindung-und-export (HEAD c811dbb)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-d-migrate-verbindung-und-export](../plan/planning/done/slice-d-migrate-verbindung-und-export.md) (Zweig `slice-d-migrate-verbindung-und-export`, HEAD `c811dbb`, Basis `6e91aec`, Commits `926fd58` bis `c811dbb`) gegen DoD §2, Plan §3, [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und `AGENTS.md` §3.2, §3.7, §3.8. Eingang: Review-Report [2026-11-05-4805061-slice-d-migrate-verbindung-und-export](2026-11-05-4805061-slice-d-migrate-verbindung-und-export.md) (0 HIGH, 2 MEDIUM, 2 LOW, 3 INFO) und die Behebungen in `4a57b46`, `25f9e2c`, `c811dbb`.
* Selbst gefahren (Arbeitsbaum sauber, HEAD `c811dbb`; vor und nach den Mutationen `git status` ohne Änderung an Quelltext):
  * `make docs-check`: 759 Dateien, 0 Befunde.
  * `make test`: **kam vollständig aus dem Cache** (alle Stages `CACHED`, 1,6 s) und ist deshalb kein Beleg. Frischbeleg: `make clean-build` (`--no-cache`, Jar, detekt, Tests), Exit 0, 8 min 33 s, Last 8 bis 12; 931 `PASSED`-Zeilen im Testbericht, 0 Tests `FAILED`; `RpcPositionalNamesTest` (7 Fälle), `FixedFormsTest`, `SimulationBackendFixedTest` (15 Fälle) bestanden; Lizenz und Manifest (`manifest-pruefung.sh`) im Bau grün.
  * `make kompatibilitaet`: grün, 13 bestanden, 51 s (Last 8,6). `make simulations-integration`: grün, 4 bestanden, 51 s.
  * Beleg am fremden Client (siehe unten), Testserver mit `TESTSERVER_NAME=tdswire-verif-vexp`, Port 24893, Image `tdswire-testserver:verif-vexp` (ID `sha256:2a5a4ebb…`); nach dem Lauf `make testserver-down` mit denselben Variablen, Container und Image entfernt, Port frei, kein Rest. Die Images und Container des Implementers (`tdswire-testserver:vexp`) und des TLS-Laufs habe ich nicht angefasst.
  * Vier Mutationen (unten), je mit `make test`, danach `git checkout` der Datei.
* Nicht selbst gefahren: `make gates` insgesamt (insbesondere `make detekt` allein, `referenztest-dotnet` als Gate-Schritt, die Selbsttests der Klassen `werkzeug` und `stempel`). Ein Beleg des Implementers für `make gates` lag mir nicht vor; detekt lief nur als Teil von `make clean-build`.

## Gesamturteil

**Closure-fähig: ja, mit Bedingungen B-1 bis B-3.** Kein HIGH, kein MEDIUM, 0 LOW, 3 INFO (V-1 bis V-3). Alle drei Liefer-Punkte sind am Bestand und am Lauf bestätigt; die Behebungen F-1 bis F-7 sind so erfolgt wie behauptet. Offen sind nur Pflichten, die der Plan selbst für die Closure vorsieht.

Bedingungen:

* B-1: `make gates` grün auf dem Stand, der geschlossen wird, mit Ausgabe in der Closure-Notiz (hier nicht reproduziert; die DoD-Zeile „`make gates` grün“ ist ohne diesen Beleg nicht abhakbar).
* B-2: Jedes Risiko aus §6 erhält einen Ausgang. Belege aus diesem Lauf: Risiko 3 (`RpcParameters` berührt den .NET-Referenzclient) ist gemessen entkräftet: `make kompatibilitaet` grün (13) und `make simulations-integration` grün (4) auf HEAD. Risiko 1 (Weitung über den Referenzclient hinaus) ist durch Lastenheft 0.7.1 für den Konstantenterm und durch [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) gedeckt. Risiko 2 (Handvektoren aus einem Treiber außerhalb des Clientprofils) bleibt weiter offen: gilt für mssql-jdbc 13.4.0 in d-migrate 1.12.0 (V-2).
* B-3: Closure-Notiz mit Steering-Loop-Eintrag, Register-Fortschreibung bzw. Vermerk in §7, DoD-Haken und Merge. Die Paarung „Folge-Slice“ für den Befund `--filter "Id = 7"` ist nicht getragen (V-3; nur Register-Eintrag, kein Träger); das ist in §7 als solches zu nennen.

## DoD-Punkte (§2)

* **Liefer-Punkt 1, Protokoll: bestätigt.**
  * `sp_executesql` mit nur dem Text: `RpcCatalog.executeSql` verlangt mindestens einen Parameter (`EXECUTESQL_TEXT_ONLY = 1`), die Deklaration ist dann `""`. Weniger als einen Parameter, ein NULL-Text, ein Integer als Text: `INVALID_RPC_FORM` (Test `der einzige Parameter muss ein Text sein`, bestanden).
  * Wertparameter ohne Namen: `RpcParameters.map` bildet `@P<i>` nach der Stelle unter den Wertparametern, nur wenn `nameInText = false` (`sp_executesql`, `sp_prepexec`, `sp_execute`); per Prozedurname bleibt der leere Name `PARAMETER_NAME`; ein doppelter Name (ohne Beachtung der Schreibweise, vor der Ersetzung geprüft) ist `PARAMETER_NAME`. Gelesen im Code und an den Tests bestätigt.
  * [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) trägt die Festlegung in einem eigenen Absatz „Text ohne Deklarationsliste und Wertparameter ohne Namen“, Messung und Setzung getrennt („Festlegung (Setzung, nicht gemessen)“); die Abgrenzung auf einen Treiber und eine Version steht im Absatz. Die Zeile der Tabelle ist auf „optional“ nachgezogen.
  * Handvektoren (`AGENTS.md` §3.8): `TEXT_ONLY` und `POSITIONAL` sind im KDoc des Tests und im Messprotokoll als Aufzeichnung von d-migrate 1.12.0 (Digest) aus einer vorübergehenden, nicht committeten Instrumentierung benannt. Die Quelle steht im Test, wie die DoD es verlangt; die Aufzeichnung selbst ist nicht reproduzierbar (V-2). Die übrigen neuen Fälle (`sp_prepexec`, `sp_execute`, OUTPUT) sind im KDoc ausdrücklich als „konstruiert, nicht aufgezeichnet“ gekennzeichnet und sagen, dass mit d-migrate weder `sp_prepexec` noch `sp_execute` gemessen wurde.
* **Liefer-Punkt 2, Anweisungsformen: bestätigt.**
  * Form C: `FixedForms` erkennt `SELECT 1` genau und `SET`-Folgen mit `ANSI_NULLS`, `ANSI_PADDING`, `ANSI_WARNINGS`, `ARITHABORT`, `CONCAT_NULL_YIELDS_NULL`, `QUOTED_IDENTIFIER`, `NUMERIC_ROUNDABORT`, `IMPLICIT_TRANSACTIONS` (ON/OFF) und `LOCK_TIMEOUT <Ganzzahl>`, alles oder nichts je Text. Das deckt die in der DoD genannten Anweisungen; die Liste entspricht der in [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) nachgetragenen. `SET` endet als `UpdateCount(0)` (`FixedAnswers`), `SELECT 1` als eine Spalte `Int32` ohne Namen, nicht NULL, Zeile 1. Der Fehler 50007 bei `ResultEnd` ohne Anfang ist in `FixedAnswers`-KDoc und Spec als Grund der Wahl genannt.
  * `WHERE 1 = 0`: `Term.Constant` nur in `select(...)` (`whereClause(constants = true)`), `SelectExecution.read()` liefert bei `never` `ResultStart` mit den Spalten und `ResultEnd(0)` ohne Zeilenquelle. In `UPDATE`/`DELETE` nicht erkannt (Test `Aenderungen mit Konstantenterm sind abgelehnt`, bestanden). Die Setzung („Konstantenterm statt nur Text `1 = 0`“) steht in SPEC-036 und ist als Setzung gekennzeichnet; gemessen ist nur `1 = 0`.
  * [SPEC-004](../../spec/spezifikation.md#spec-004--testprofil) verweist auf Form C und sagt, dass kein Anweisungsprofil des Referenzclients gefüllt wird.
* **Liefer-Punkt 3, Beleg am fremden Client: bestätigt, selbst nachgefahren** (siehe unten) und im Messprotokoll [d-migrate-verbindung-und-export-2026-11-05](../messungen/d-migrate-verbindung-und-export-2026-11-05.md) mit Exit-Code, Auszug, Rechner, Last, Image-Digest (Client und Server-Image) und dem Stand des Quelltexts belegt. Was danach scheitert, ist gemeldet und je einem Träger zugeordnet (Katalog, Schreibpfad) bzw. dem Register (V-3).
* **Nachweis-Pflichten:** Review-Report liegt vor, die Behebungen sind in drei Commits getrennt nach Befund; `make gates` grün: berichtet nicht, hier nicht reproduziert (B-1); Doku-Update: `docs/user/testserver-manuell.md` zeigt die Grenze zu JDBC nach (eine Zeile), Spec, Lastenheft und Messprotokoll sind nachgezogen; Closure-Notiz, Register-Vermerk, Risiko-Ausgänge, Paarungen und DoD-Haken sind offen und kein Befund.

## Beleg am fremden Client (selbst gefahren)

d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, URL `mssql://alice:secret@127.0.0.1:24893/manuell?encrypt=false`, `docker run --rm --network host`, Testserver aus dem Quelltext von HEAD `c811dbb`, Beschreibung `src/testserver/resources/manuell/sim.yaml`, Last 12,7 bei Beginn (`uptime`, 13:51, Einzellauf).

| Befehl (gekürzt) | Exit | Ergebnis |
|---|---|---|
| `data export --format json --tables kunden -o /work/kunden.json` | 0 | „Exported 1 table(s) (0 rows, 0.00 MB)“, Datei `[]` |
| `data export --format yaml --tables bestellungen -o /work/best.yaml` | 0 | 0 Zeilen |
| `data export --format json --tables kunden --filter "Name IS NULL"` | 0 | 0 Zeilen, Datei `[]` |
| `data export ... --filter "Name = 'Ada'"` (zusätzlich) | 0 | 0 Zeilen (Wert als Parameter ohne Namen, `@P0`) |
| `data export --format json` (ohne `--tables`) | 4 | „Failed to list tables: Unsupported SQL.“ (bestätigt) |
| `schema reverse --output /work/rev.yaml` | 4 | „Connection or metadata error: Unsupported SQL.“ (bestätigt) |
| `data export ... --filter "Id = 7"` (zusätzlich) | 5 | „Failed to export table 'kunden': Invalid value.“ (bestätigt; Ursache `bigint` gegen `Int32` bleibt aus dem Code gelesen, nicht von mir gemessen) |

Die Messung stützt die Aussage auf dem letzten Stand des Backends, den das Messprotokoll selbst für `39c8872` nannte (V-3 zu F-5). Ein Server-Log für Anweisungen gibt es nicht; was der Server empfing, steht nur im Ausgang des Clients (Messgrenze wie im Protokoll). Leere Tabellen: Zeilen im Export sind nicht belegt.

## Behebungen der Review-Findings

| Finding | Behauptung | Befund des Verifiers |
|---|---|---|
| F-1 (MEDIUM) | Lastenheft 0.7.1 erlaubt den Konstantenterm ausdrücklich | bestätigt. Version 0.7.1, Beschreibung, Happy Path, Ausnahme bei Negative und bei Out-of-Scope, Änderungsverlauf. Der Wortlaut „Vergleich zweier Ganzzahlkonstanten als Filter einer Auswahl“, „ausgenommen … in Änderungs- und Löschanweisungen nicht erkannt“ deckt `<Ganzzahl> = <Ganzzahl>` nur in S1 (Parser: `constants = true` allein in `select`). SPEC-036 verweist nur noch auf die Aufnahme („Das Lastenheft nimmt ihn ausdrücklich auf“), kein Selbstzitat der alten Lesart. Die Quelle der Freigabe ist die Commit-Nachricht (V-1). |
| F-2 (MEDIUM) | Tests für `@P<i>` bei `sp_prepexec`, `sp_execute`, OUTPUT; Mutationen rot | bestätigt, selbst gefahren (Tabelle unten). Die Fälle sind konstruiert aus `RpcWire` und so gekennzeichnet; der KDoc benennt den Wächter. |
| F-3 (LOW) | Register-Eintrag zum Parametertyp | bestätigt: `observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/` mit `observation.md`, `state.md` („offen — 1×, unter der Schwelle; kein Träger“, Hinweis auf die beiden Schreib-Slices) und `evidence/slice-d-migrate-verbindung-und-export.md`; das Messprotokoll verlinkt ihn. Auflösbare Links (`make docs-check` 0 Befunde). Die Ursache bleibt als „aus dem Code gelesen, nicht gemessen“ gekennzeichnet. |
| F-4 (LOW) | KDoc-Zeile in `RpcCatalog` | bestätigt: ` * Festlegungen (ohne Orakel): …` trägt den Stern. |
| F-5 (INFO) | Messstand im Protokoll | bestätigt, als Offenlegung: das Protokoll nennt `39c8872` und dass die Läufe nicht auf `75ca5c7` wiederholt sind. Mein Lauf auf HEAD gibt dasselbe Ergebnis (Exit 0 und 0 Zeilen für kunden, bestellungen, `IS NULL`); das Protokoll selbst wurde dadurch nicht erneuert. |
| F-6 (INFO) | `IMPLICIT_TRANSACTIONS` nicht gemessen | bestätigt: Protokoll („in keinem Lauf hier“) und SPEC-036 („hier nicht erneut gemessen“, „nicht modelliert“) sagen es; Setzung gekennzeichnet. |
| F-7 (INFO) | Chronik aus der Spec | bestätigt: im Bestand von `spec/spezifikation.md` kein „Vorher“/„verlangte“ im Zusammenhang mit SPEC-030/-036; die neuen Absätze beschreiben den Ist-Zustand. Die Zeile im Änderungsverlauf der Spec (2026-11-05) ist die vorhandene Tabelle, nicht Prosa. |

Die vier Mutationen, je einzeln mit `make test` (HEAD, danach zurückgesetzt):

| Mutation | Ergebnis |
|---|---|
| `RpcBinder.kt` Zeile 84 (`ExecuteSql`): `nameInText = true` | rot: `RpcPositionalNamesTest` (`ein Wertparameter ohne Namen heisst P0`, `die Stelle bestimmt den Namen`, `benannte und unbenannte Parameter mischen`, `ein Parameter ohne Namen mit OUTPUT-Bit heisst P0`) und `RpcBinderTest` (`Doppeltes At-Zeichen …`) |
| Zeile 89 (`PrepExec`): `nameInText = true` | rot: `sp_prepexec und sp_execute benennen Werte ohne Namen` |
| Zeile 92 (`Execute`): `nameInText = true` | rot: derselbe Test (der Test prüft beide Aufrufstellen in einem Fall; jede Mutation färbt ihn einzeln rot) |
| `RpcParameters.kt`: Namensbildung nur ohne OUTPUT-Bit (`&& !parameter.output`) | rot: `ein Parameter ohne Namen mit OUTPUT-Bit heisst P0` |

## Plan-vs-Code-Diff (§3)

| Plan | Code | Urteil |
|---|---|---|
| `spec/spezifikation.md` SPEC-030, SPEC-036, SPEC-004 | alle drei geändert, Setzung gekennzeichnet; zusätzlich Änderungsverlauf | gedeckt |
| `RpcCatalog.kt`, `RpcParameters.kt` | beide geändert | gedeckt |
| `simulation/` (Parser, Planer, Backend) | `StatementParser`, `StatementPlanner`, `SimulationBackend` geändert | gedeckt |
| `FixedForms.kt`, `FixedAnswers.kt` (neu), `Statement.kt`, `SimConditions.kt` | vorhanden, alle `internal` | gedeckt |
| Tests und .NET-Referenztests | `RpcPositionalNamesTest`, `FixedFormsTest`, `SimulationBackendFixedTest` neu; `RpcBinderTest`, `RpcFailureTest`, `StatementFormsTest`, `SimulationBackendSelectTest` angepasst; `SzenarienTests.cs` `SELECT 1` → `SELECT 2` | gedeckt; die Anpassungen erhalten die Zusage („nicht erkannte Anweisung ist Anfragefehler, Verbindung bleibt nutzbar“), weil `SELECT 1` jetzt Form C ist |
| Nicht im Plan, im Diff | Messprotokoll, Nutzer-Doku (1 Zeile), Register-Eintrag, Lastenheft 0.7.1, Review-Report, Zeile „Verantwortlich“ im Slice | von der DoD, den Findings oder dem Start-Trigger verlangt; Lastenheft-Änderung siehe V-1 |

Keine Änderung an `config/`, `build.gradle.kts`, `Dockerfile`, `Makefile`, `tools/`, `harness/` (Diffstat leer): kein Gate gelockert (§3.6).

## Konformität zu Entscheidungen und Hard Rules

* [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) (Produktschnitt Bibliothek und Testserver): die Formen liegen in der Bibliothek (`simulation/`), der Testserver wird unverändert verwendet. Konform. [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md): alle neuen Namen sind `internal` (`FixedForms`, `FixedAnswers`, `FixedForm`, `SetOption`, `Term.Constant`/`Named`, `RowTerm.Constant`, `PlanResult.Fixed`, `Recognition.Fixed`, `asciiUpper`, `isKeyword`); die öffentliche Fläche (`SimulationBackend`) ändert nur KDoc und das Verhalten. Konform. Die Dateinamen beider ADRs habe ich aufgelöst und die Entscheidungen nicht im Wortlaut neu abgeglichen, soweit der Diff keine ADR-Stelle berührt; ein Verstoß ist mir nicht aufgefallen.
* [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend): Happy Path (`1 = 0` liefert Beschreibung, keine Zeile), Boundary (Bedingung ohne Treffer: gültige Metadaten, ordnungsgemäßer Abschluss), Negative (Ausdrücke außerhalb weiter `TDS_UNSUPPORTED_SQL`, z. B. `UPDATE … WHERE 1 = 1`) sind durch Tests belegt. Das Backend wertet nur den Vergleich zweier Ganzzahlen aus, das Lastenheft erlaubt genau das. Konform. [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate): Prüfclient d-migrate, Export einer Tabelle ohne Zeilen: am Lauf belegt.
* `AGENTS.md` §3.2 (Suppression-Verbot): im gesamten Diff kein `@Suppress`, `@SuppressWarnings`, `noqa`, `nolint`; `make clean-build` fährt detekt grün. Konform.
* §3.7 (Kommentare): die neuen KDocs beschreiben Zusage, Setzung, Abgrenzung („Setzung nach der Aufzeichnung …“); kein Konjunktiv über Verworfenes gefunden. Der Rest einer Teilersetzung (F-4) ist behoben. Konform.
* §3.8 (Aussage trägt so weit wie Beleg): Setzungen sind als solche gekennzeichnet, Messungen mit Version, Datum und Digest; die Grenzen (ein Treiber, eine Version, `IMPLICIT_TRANSACTIONS` nicht gemessen, `bigint`-Ursache gelesen) stehen im Text. Konform, mit V-2.
* Commit-Nachrichten: alle zehn Commits nennen mindestens eine `TDS-*`- oder `ADR-*`-Kennung.

## Befunde des Verifiers

| ID | Kategorie | Befund |
|---|---|---|
| V-1 | INFO | Lastenheft 0.7.1 ändert Rang 1. Die Freigabe („Nutzerentscheidung vom 2026-11-05 zu F-1“) steht nur in der Commit-Nachricht von `c811dbb` und im Änderungsverlauf ohne Freigebenden; ich kann sie aus dem Repo nicht prüfen. Die Spec-Seite folgt dem Lastenheft, nicht umgekehrt. Hinweis an den Planner, die Entscheidung in §7 zu nennen. |
| V-2 | INFO | Die Aufzeichnung der zwei Vektoren stammt aus nicht committeter Instrumentierung; die Quelle steht im Test, ihre Reproduktion nicht (das Protokoll sagt es offen). Ich habe die Bytes nicht neu aufgezeichnet; dass beide Formen (Text ohne Parameter, Wert ohne Namen als `@P0`) am Server mit dem echten Client ankommen, belegen die Läufe `data export` und `--filter "Name = 'Ada'"`. Gilt für einen Treiber, eine Version (Risiko 2 bleibt offen). |
| V-3 | INFO | Der Befund `--filter "Id = 7"` (Exit 5) hat nur einen Register-Eintrag und keinen Folge-Slice; das ist ehrlich ausgewiesen („kein Träger“), aber die Paarung „Folge-Slice“ ist für ihn nicht getragen. Das Messprotokoll ist nicht auf HEAD wiederholt; mein Lauf (oben) gibt dasselbe Ergebnis. |

## Zusammenfassung

HIGH 0 · MEDIUM 0 · LOW 0 · INFO 3. Die Findings F-1 bis F-7 des Reviews sind behoben wie berichtet, die drei Liefer-Punkte der DoD sind am Bestand und am Lauf bestätigt. Offen bis zur Closure: B-1 (`make gates`), B-2 (Risiko-Ausgänge), B-3 (Closure-Notiz, Register, Haken, Merge).
