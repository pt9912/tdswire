# Review-Report: slice-schreibpfad-merge-output — 2026-11-07

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Diff `f5250140..b2ff295c` (51 Dateien, Commits `868e4839` bis `b2ff295c`: Moves, Messung mit d-migrate, Form M in der Bibliothek, `onUpdate` im Bestand, Messprotokoll und Plan, Abdeckungsmatrix), Zweig `worktree-agent-a367dd0d063325ded`. `main` steht auf `f5250140` (Basis, unbewegt): der Merge von `main` ist sauber; der Zweig `worktree-agent-a9764e63162fbe637` (Nutzer-Doku) konfligiert in `docs/user/testserver-manuell.md`, `harness/README.md` mischt automatisch (F-12).

**Skill:** `.harness/skills/reviewer.md` @ `b2ff295c` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-07

**Eingangs-Kontext** (die Verträge, gegen die geprüft wurde):

- Slice `slice-schreibpfad-merge-output` (Plan §3, DoD, Rückführungen, §6 Risiken), `welle-testserver-manuell`, `slice-abnahme-d-migrate`, `slice-bulk-load-feste-typen-und-referenzclient`, `slice-antwort-tokens-zielbild-luecken-bauen`
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Accepted: Zerlegung, Liste der öffentlichen Namen, Nr. 4, Folgepflicht 2), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md)
- `SPEC-030`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`; [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)
- `AGENTS.md` §3.1 bis §3.8; Grundsatz des Nutzers „Spec ist Zielbild“ (der echte SQL Server ist die Referenz; Abweichungen sichtbar mit Träger)

## Selbst gefahren

| Lauf | Ergebnis |
|---|---|
| `make gates` | Exit 0. Erster Lauf ohne Belege (alle Gruppen gelaufen, Stage `test` 36 min 1 s unter fremder Last, 1167 `PASSED`, alle 15 `SimulationBackendMergeTest`, `MergeEmbeddingTest`, `TableStoreTest`, `ListenerAbortTest` grün); zweiter Lauf 9 s, drei Gruppen bei Beleg übersprungen |
| `make gates FORCE=1` | nicht gesondert gefahren: der erste Lauf hatte keinen Beleg an einem Vorfahren und hat jede Gruppe ausgeführt (Log: `kein Beleg fuer … mit diesem Eingangs-Hash`) |
| `make test` | Exit 0 in 1 s: Cache-Treffer (Cache-Falle); der Frischbeleg ist der erste `make gates`-Lauf oben und `make matrix-zitate` |
| `make flaeche`, `make docs-check`, `make skript-selbsttest` | grün; `docs-check`: 1182 Dateien, 0 Befunde; `skript-selbsttest` im Cache |
| `make e2e` | Exit 0, 8 Stufen, 186 s |
| `make matrix-zitate` | Exit 0 in 6 min 39 s: 1167 bestandene Testfälle, 304 Zitate, 0 ohne PASSED. Die „>25 Min“ des Implementers sind nicht wiederholt (F-14) |
| `make clean-build` | Exit 0 in 10 min 43 s (`--no-cache`, Last 14 bis 22): 1167 `PASSED`, detekt grün |
| `make vergleichslauf-d LAUF=jdbc-2022` und `jdbc-2025` (EULA bestätigt, `MESSUNG_NUR` mit allen zwölf Schritten, also ohne Ablage in den Arbeitsbaum) | beide Exit 0, „gruen“, 83 s und 92 s; Vergleich mit der abgelegten Aufnahme (Kennungen und Verbindungsnummern normalisiert, nach Zeilen sortiert): Inhalt gleich bis auf je eine Handvoll Anmeldezeilen einer einzelnen Verbindung (Zeitpunkt des Mitschnitts); Wortlaut des `MERGE`, Reihenfolge `sp_executesql`, `sp_prepexec`, `sp_execute`, `NVARCHAR(10)`, `0x0117`, kein `sp_unprepare`, Exits (0, 0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0) wie in `M-25` bis `M-29` |
| d-migrate (Digest `sha256:308f2775…`) gegen zwei Testserver `ts-a` (14341) und `ts-b` (14342) aus dem Abbild des Stands `b2ff295c` (saubere Arbeitskopie), Beschreibung mit `kunden`, `kunden_int` (INT), `bestellungen` (DECIMAL), `zeiten` (TIME, DATETIME2), `gross` | S1 bis S8 des Messprotokolls wiederholt: gleiche Zähler und Exits (S1 „3 inserted“, S2 „1 inserted, 2 skipped“, S3 „3 updated“, S4/S5 Export B gleich Export A byteweise, S6 „3 inserted“, S7 „1 inserted, 2 updated“, S8 Exit 5); weitere Läufe: D1, D3 und `data transfer --tables bestellungen --on-conflict skip` scheitern (F-1); D2, D4 und der Transfer ohne `--on-conflict` laufen; G1/G2 2500 Zeilen `skip`/`update` Exit 0 (ein Handle je Verbindung, keine Handle-Grenze berührt); P1 drei gleichzeitige `skip`-Importe derselben Datei: ein Lauf Exit 0 („911 inserted, 1,589 skipped“), zwei Läufe Exit 5 „Backend error.“ (F-8) |
| Mutationen | siehe Abschnitt Mutationen |

## Findings

### F-1 MEDIUM — `MERGE` in Tabellen mit DECIMAL-, TIME- oder DATETIME2-Spalten scheitert; die Lücke hat keinen Träger und liegt vor `slice-abnahme-d-migrate`

* kategorie: MEDIUM
* quelle: [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Liste „was der MERGE-Pfad sonst braucht“, Zeile a); Slice-Kopf „Berührte Spec-Stellen: … `SPEC-030` (Parametertypen)“ und Slice §3 „Anschluss an die Entscheidung“; [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcParameters.kt` · „`DATETIMN`, `DATETIMEOFFSETN`) ist [RpcRejection.TYPE]“; `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/RpcParameterReader.kt` · „Wert mit $length Byte, erwartet $expected“
* befund: Selbst gefahren am Stand `b2ff295c`: `data import --on-conflict skip` in `bestellungen` (DECIMAL(10,2), die Beispiel-Tabelle der mitgelieferten `testserver/resources/manuell/sim.yaml`) endet mit Exit 5 „Protocol error.“ (Server: `TDS_PROTOCOL_ERROR`), in `zeiten` (TIME, DATETIME2) mit „Unsupported RPC.“; ebenso `data transfer --tables bestellungen --on-conflict skip` (Exit 5), während derselbe Transfer und der Import im Standardpfad laufen. Ursache: der RPC-Parser verlangt für `DECIMALN` genau 17 Byte (d-migrate sendet 2 bis 4) und weist `DATETIMN` ab (Messung a7 des Spikes, 2026-11-05); der Slice hat die in [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) genannten und in seinem Kopf (`SPEC-030`) geplanten Änderungen nicht gebaut, und `SPEC-030` ist im Diff unberührt. Ein offener Slice oder Register-Eintrag für den Parameter-Weg fehlt (`slice-codec-datentypen-erweiterung` A-11 betrifft die Antwortseite von `DECIMALN`; `slice-abnahme-d-migrate` und `slice-bulk-load-feste-typen-und-referenzclient` nennen den Parameter-Weg nicht). Folge: der Validator-Lauf der Abnahme, der `data transfer` ohne `--tables` oder `--on-conflict` auf den Beispiel-Tabellen fährt, bricht an dieser Stelle.
* verifizierbar: ja (die zwei d-migrate-Läufe oben; ein Draht-Test mit `DECIMALN` von 4 Byte und `DATETIMN` als RPC-Parameter)
* klasse: Geplante Spec-Änderung nicht geliefert und ohne Träger

### F-2 MEDIUM — Nutzer-Doku und Spec nennen die Lücke von F-1 nicht und sagen „nicht gemessen“, wo die Ablehnung bekannt ist

* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8 (Aussage trägt höchstens so weit wie ihr Beleg), Reviewer-Skill „Aussage breiter als ihr Beleg“
* pfad: `docs/user/testserver-manuell.md` · „Der Testserver legt eine fehlende Zeile an, lässt eine vorhandene bei `skip` unverändert und ändert sie bei `update`“; `spec/spezifikation.md` · „Spalten mit `DECIMAL`, `TIME`, `DATETIME2` und `BIT` im `MERGE` von d-migrate“
* befund: Die Doku sagt, `--on-conflict` und `data transfer` mit `--on-conflict` liefen, ohne die Bedingung (nur Tabellen ohne DECIMAL-, TIME- und DATETIME2-Spalten), und nennt dieselbe Doku `bestellungen` mit `Betrag` als Beispiel-Tabelle. `SPEC-036` Form M führt die Spalten als „Nicht gemessen“; die Ablehnung durch den RPC-Parser ist gemessen (Spike a7) und hier wiederholt (F-1). Auch das Messprotokoll `merge-output-2026-11-07` nennt die Typen unter „Nicht gemessen“ und hält S1 bis S8 auf `BIGINT`, `INT`, `DATE` und `NVARCHAR`.
* verifizierbar: ja (Lauf D1/D3 aus F-1 gegen die Aussage der Doku)
* klasse: Zusage ohne ihre gemessene Bedingung

### F-3 MEDIUM — Drei Zielbild-Abweichungen der Antwort (Länge von `$action`, Befehl `0x0117`, Fehlernummern) tragen als Träger nur den Slice selbst

* kategorie: MEDIUM
* quelle: Grundsatz „Spec ist Zielbild“ (Abweichung sichtbar mit Träger); Slice-DoD „Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen“; [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4
* pfad: `spec/spezifikation.md` · „beides ist **nicht gebaut**“; `docs/messungen/referenzabgleich.md` · „Länge der Spalte `$action` (Server 10 Zeichen, Bibliothek 4000)“; `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt` · „Änderung fehlgeschlagen“
* befund: Die Spec nennt die Abweichungen offen („Entscheidung des Nutzers“), und die Spalte „Träger“ der Messzeilen zeigt auf den Slice selbst, der mit Closure `done/` wird. `slice-antwort-tokens-zielbild-luecken-bauen` führt `curCmd` und nennt MERGE `0x0117` nur in einem Risiko („nur dort gebaut, wo W-6 und M-13 messen“), die Länge von `$action` (neues Feld an `ResultColumn`) und die Fehlernummern 2628, 2627 und 208 aus `M-15` (Bibliothek: `TDS_BACKEND_ERROR` mit fester Meldung, der Bulk-Pfad dagegen 2627) stehen in keinem offenen Slice und keinem Register-Eintrag. Ob d-migrate sie auswertet, ist nicht gemessen; die Läufe S1 bis S8 hängen an keinem.
* verifizierbar: nein (Planner-Entscheidung: Träger benennen)
* klasse: Zielbild-Abweichung ohne dauerhaften Träger

### F-4 MEDIUM — Die Prüfung `stopped` zwischen Lesen und Callback ist Eigencode, dessen Entfernung keinen Test rot macht

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“; [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) (Cancel/ATTENTION)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt` · „if (action == null || stopped.get()) 0L“
* befund: Mutation einzeln gefahren (`|| stopped.get()` entfernt, `:test` mit `SimulationBackendMergeTest`, `SimValuesTest`, `SimulationBackendSelectTest`, `:testserver:test`, `:integratorTest`): `BUILD SUCCESSFUL`, kein Test rot. Der Test „eine beendete Ausfuehrung liest nichts und ruft nichts“ setzt `stop` vor dem Sammeln und fängt die Zeile am Anfang des Flusses. Szenario: ein Abbruch, der zwischen Ende des Lesens und Aufruf des Callbacks eintrifft, schreibt trotz ATTENTION noch in den Bestand.
* verifizierbar: ja (Test mit einer Zeilenquelle, die in `next()` abbricht)
* klasse: Abbruch-Prüfung ohne Test

### F-5 MEDIUM — Plan-Tabelle §3 nennt die berührten Dateien nicht vollständig, der Kopf nennt eine Spec-Stelle, die nicht berührt ist

* kategorie: MEDIUM
* quelle: Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“
* pfad: `docs/plan/planning/done/slice-schreibpfad-merge-output.md` · „| `testserver/kotlin/io/github/pt9912/tdswire/testserver/TableStore.kt` | update |“
* befund: Im Diff berührt, in der Tabelle ohne Zeile: `testserver/kotlin/.../RowRules.kt` (neu), `src/integrator/kotlin/.../RpcWire.kt` (drei Helfer), `src/test/.../SimulationBackendSelectTest.kt` (Testwert geändert), `harness/README.md` (Zelle `make vergleichslauf-d`), `docs/plan/spec-abdeckung.md`. Der Kopf „Berührte Spec-Stellen“ nennt `SPEC-030`, die im Diff unberührt ist (F-1),. Der Negativbefund „keine Datei darüber hinaus“ gilt damit nicht.
* verifizierbar: ja (Abgleich Diff gegen Tabelle)
* klasse: Plan-Tabelle ohne berührte Datei

### F-6 LOW — Der Abschnitt „Plan (vor Code)“ trägt seinen Inhalt erst im letzten Commit

* kategorie: LOW
* quelle: `AGENTS.md` §3.8; Slice-Hülle („der Implementer erweitert §3 im ersten Lauf“)
* pfad: `docs/plan/planning/done/slice-schreibpfad-merge-output.md` · „**Ist-Zustand vor dem Bau gemessen:**“
* befund: Die Tabelle und der Absatz „Zerlegung und Reihenfolge (Plan vor Code)“ stehen erst in Commit `23242f68` (nach `8fc2fea6` Messung, `30eae2d9` Bibliothek, `fd7ffe73` Testserver); `22012212` ändert nur den Verantwortlichen. Dass der Ist-Zustand „vor dem Bau“ gemessen wurde, ist aus der Historie nicht belegt.
* verifizierbar: ja (`git log -p` der Datei)
* klasse: Plan nach dem Code geschrieben

### F-7 LOW — Lücken der Negativ- und Randtests der neuen Form

* kategorie: LOW
* quelle: [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (Tests: Happy/Boundary/Negative); [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendMergeTest.kt` · „fun `Schreibweise, Umbrueche und Aliase aendern die Erkennung nicht`“; `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/MergeEmbeddingTest.kt` · „client.executeSql(“
* befund: (a) Kein Test hat einen Schlüssel aus zwei Spalten, obwohl `SPEC-036` Form M ihn nennt („in beliebiger Reihenfolge“); eine Probe mit zwei Spalten in vertauschter Reihenfolge (Wegwerf-Test, nicht committet) war grün. (b) Der Draht-Test fährt nur `sp_executesql`; die Folgezeilen von d-migrate (`sp_prepexec`, `sp_execute`) sind nur von Hand belegt. (c) Kein Draht-Test für den Fehler hinter `COLMETADATA` mit Folgeabfrage und keiner für Cancel mitten im `MERGE` (F-4). (d) „nicht erkannte Gestalten“ fasst neun Abwandlungen in einem Test zusammen und prüft nur den Code `TDS_UNSUPPORTED_SQL`, nicht den Grund.
* verifizierbar: ja
* klasse: Negativtest für neuen Pfad fehlt

### F-8 LOW — Prüfen und Ändern sind nicht atomar; die Folge am Testserver steht nirgends

* kategorie: LOW
* quelle: Bewertung der Nebenläufigkeit (Auftrag); `SPEC-036` Form M
* pfad: `spec/spezifikation.md` · „zwei gleichzeitige `MERGE` mit demselben neuen“; `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt` · „Prüfen und Ändern sind nicht atomar“
* befund: Die Spec nennt „beide können `INSERT` melden“ und „der Bestand tut das“ (Schlüsselprüfung), aber nicht die Folge am Testserver: im Lauf P1 (drei gleichzeitige `skip`-Importe) endeten zwei mit Exit 5 „Backend error.“ statt mit „skipped“ (Verlierer des Einfügens bekommt `TDS_BACKEND_ERROR`). Die Bibliothek wiederholt und sperrt nicht (anders als bei der Strukturänderung, wo sie unter einer Sperre neu plant); die Entscheidung dazu ist als Setzung vertretbar, ob der echte Server ohne Sperrhinweis ebenso scheitert, ist nicht gemessen. Die Nutzer-Doku schweigt.
* verifizierbar: ja (Lauf P1)
* klasse: Nebenläufigkeitsfolge nicht beschrieben

### F-9 LOW — `SPEC-036` führt „Aliase für Tabellen und Spalten“ weiter als nicht erkannt

* kategorie: LOW
* quelle: Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“
* pfad: `spec/spezifikation.md` · „Aggregate und `GROUP BY`, `DISTINCT`, `TOP`, `ORDER BY`, `OFFSET`/`FETCH`, Aliase für Tabellen und“
* befund: In der Liste „Nicht erkannt“ erhielt nur `OUTPUT`-Klauseln die Ausnahme „(außer `OUTPUT $action` der Form M)“; Form M verlangt Aliase für Tabelle und Quelle (`AS tgt`, `AS src`).
* verifizierbar: nein
* klasse: Fortschreibung lässt Ausnahme in der Liste aus

### F-10 INFO — Mutationen einzeln gefahren: sieben von acht rot wie gemeldet, eine bleibt grün

* kategorie: INFO
* quelle: Reviewer-Skill „Mutationsbeschreibung nennt die gedachte statt der gemessenen Wirkung“
* pfad: `docs/messungen/merge-output-2026-11-07.md` · „14 Mutationen auf einmal angewandt“
* befund: Siehe Abschnitt Mutationen: die Zuordnung der Protokollzeilen N1, N2, N4, N6, N9, N10 und N12 stimmt mit den Einzelläufen überein (bei N1 und N9 ist zusätzlich `TableStoreTest` rot); N3, N5, N7, N8, N11, N13, N14 nicht einzeln gefahren. Das Protokoll nennt selbst, dass die Zuordnung aus Assertions gelesen ist und dass Skript und Kopie nicht im Repo liegen.
* verifizierbar: ja
* klasse: Mutationszuordnung gemeinsam gelesen

### F-11 INFO — Beobachtungs-Register und Risiken sind bis zur Closure auf dem alten Stand

* kategorie: INFO (Planner)
* quelle: Slice §6; `AGENTS.md` §3.7 (Zustandsfelder)
* pfad: `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md` · „Bis dahin trägt die Messreihe nur den Referenzclient“
* befund: Der Stand-Text spricht vom Messauftrag mit dem JDBC-Client als künftig; `M-25` bis `M-30` liegen vor. Die Ausgänge der Risiken in §6 und die Closure-Notiz sind offen und gehören dem Planner nach der Verifikation; kein Haken im Diff gesetzt (geprüft).
* verifizierbar: nein
* klasse: Zustandsfeld bis Closure veraltet

### F-12 INFO — Konflikt mit dem Zweig der Nutzer-Doku

* kategorie: INFO
* quelle: Auftrag (Merge-Konflikte nur bewerten)
* pfad: `docs/user/testserver-manuell.md` · „Mit `--on-conflict skip` oder `--on-conflict update` sendet d-migrate“
* befund: `git merge-tree` gegen `worktree-agent-a9764e63162fbe637`: ein Inhaltskonflikt in dieser Datei (zwei Hunks: der Absatz zu `--on-conflict` und der Punkt zu `INT`/`DECIMAL`), `harness/README.md` mischt. Der andere Zweig führt in seiner Fassung weiter „laden im Standardpfad (ohne `--on-conflict`)“ und „`INT`-Spalten … scheitern im Treiber“ ohne die Ausnahme für `--on-conflict`; bei der Auflösung ist der Inhalt zu übernehmen (mit der Bedingung aus F-2). Gegen `main` (`f5250140`) sauber.
* verifizierbar: ja (`git merge-tree`)
* klasse: Textkonflikt mit Parallelzweig

### F-13 INFO — Messqualität: Belege wiederholt, Client-Grenze benannt

* kategorie: INFO
* quelle: Reviewer-Skill „Messzahl aus einem Erzeuger, der nicht im Repo liegt“
* pfad: `docs/messungen/referenzabgleich.md` · „Erzeuger im Repo (wiederholbar)“; `tools/vergleichslauf-d.sh` · „jdbclauf()“
* befund: Der Erzeuger der Messung am echten Server liegt im Repo (`tools/jdbc-messung/Aufnahme.java`, `jdbclauf`) und läuft ohne Eingriff wieder (beide Instanzen, siehe Tabelle); Wortlaut, Reihenfolge der Verbindungen, Antworten und Exits der Stichprobe stimmen mit `M-25` bis `M-29` überein; das Kennwort ist in der Ablage durch `***` ersetzt (kein Treffer für „passw“ und „secret“ in beiden Ablagen, der Lauf prüft mit `grep -qF`). Der Proxy schneidet pro Nachricht bis zum EOM-Bit mit und leitet unverändert weiter; der Durchlauf liest nur und nutzt die Bibliothek dieses Repos nicht, ein Fix der Paketierung der Bibliothek ist für ihn nicht nötig. Die Grenzen stehen im Protokoll: nur d-migrate 1.12.0, drei Zeilen je Verbindung (der Handle-Weg am echten Server über 1000 Zeilen ist nicht gemessen; am Testserver liefen 2500 Zeilen mit einem Handle je Verbindung). Die Zahlen S1 bis S8 des Testserver-Protokolls kommen aus einem Skript außerhalb des Repos auf einem nicht committeten Stand (benannt); sie sind hier am Stand `b2ff295c` gleich wiederholt. Das Kennwort der Wegwerf-Instanz steht in der Prozessliste (URL im `docker run`), was für ein zufälliges, an den Lauf gebundenes Kennwort folgenlos ist.
* verifizierbar: ja
* klasse: Messung wiederholt, ohne Abweichung

### F-14 INFO — Laufzeit der Stage `test`

* kategorie: INFO
* quelle: Maintainability
* pfad: `docs/messungen/merge-output-2026-11-07.md` · „Last (`uptime`, 1/5/15 min) 12,31/15,58/14,71“
* befund: Der erste `make gates`-Lauf brauchte für die Stage `test` 36 min 1 s, davon rund 33 min zwischen `:compileTestKotlin` und der ersten Testzeile des Wurzelmoduls (Load 8 bis 14 durch fremde Builds, Kotlin-Compiler mit 10 % CPU); `make matrix-zitate` danach 6 min 39 s. Die Ursache der „>25 Min“ ist Last, nicht ein Hang eines Tests; nicht weiter zerlegt. `ListenerAbortTest` war im Gate-Lauf grün (Lastkandidat).
* verifizierbar: nein
* klasse: Laufzeit unter Last

## Mutationen (selbst gefahren)

Acht Wegwerf-Kopien des Stands `b2ff295c` (je eine Mutation), `docker build --target test` mit gefilterten Tests (`:test` auf `SimulationBackendMergeTest`, `SimValuesTest`, `SimulationBackendSelectTest`, `:testserver:test` auf `TableStoreTest`, `:integratorTest` auf `MergeEmbeddingTest`, `--continue`, `cleanTest`). Eine Probe-Kopie mit einem Wegwerf-Test (zwei Schlüsselspalten) ist grün.

| Nr. (Protokoll) | Mutation | Rot |
|---|---|---|
| m1 (N1) | `matches` liefert immer wahr | `SimulationBackendMergeTest > die Bibliothek prueft den Schluessel …`, `… fehlender Schluessel ruft onInsert …`; `TableStoreTest > ein MERGE fuegt ein, laesst bei skip …`; `MergeEmbeddingTest` |
| m2 (N2) | Prüfung auf NULL im Schlüssel entfällt | `SimulationBackendMergeTest > NULL im Schluessel trifft nie …` |
| m3 (N4) | Bereichsprüfung `value in INT_RANGE` entfällt | `SimulationBackendMergeTest > Int64-Parameter in einer Int32-Spalte …`, `SimulationBackendSelectTest > ein ungueltiger Wert endet vor dem Lesen`, `SimValuesTest > Parameter werden erweitert und im Wertebereich …` |
| m4 (N6) | `source.close()` entfällt | `… fehlender Schluessel ruft onInsert …`, `… vorhandener Schluessel ohne Update-Zweig …` |
| m5 (N9) | Ersatz `onInsertBatch` entfällt | `… fehlender Schluessel mit Update-Zweig fuegt ein, auch ueber onInsertBatch`, `TableStoreTest > ein MERGE fuegt ein, …` |
| m6 (N10) | Alias vor dem Punkt wird nicht geprüft | `… nicht erkannte Gestalten enden vor dem Lesen` |
| m7 (N12) | Eindeutigkeit in `TableStore.update` entfällt | `TableStoreTest > onUpdate weist ungueltige Aenderungen ab …` |
| m8 (neu) | `|| stopped.get()` vor dem Callback entfällt | **keiner** (`BUILD SUCCESSFUL`), F-4 |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../simulation` (`MergeParts`: Namen, Quoting, Mehrspalten-Vergleich, optionales `WHEN MATCHED`, Reihenfolge der Klauseln, `OUTPUT $action`; `SimulationMerges`: Schlüsselprüfung, NULL-Schlüssel, höchstens ein Callback, `onInsertBatch` mit einer Zeile, Update ohne `onUpdate` abgewiesen, Quelle genau einmal geschlossen, Fehler der Quelle und des Callbacks hinter `COLMETADATA`) | geprüft, außer F-4, F-7, F-8 ohne Befund; Zwei-Spalten-Schlüssel in vertauschter Reihenfolge funktioniert (Probe) |
| `SimValues` (Verengung `Int64` in `Int32`) | geprüft, ohne Befund: Bereichsgrenzen beider Seiten getestet, einzige Verengung; wirkt auch auf Auswahl und Änderung (gewollt, Register `BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte`) |
| `StatementParser`, `StatementPlanner`, `SimulationBackend`, `SimulationChanges` (Verdrahtung, `comparesKey`, Objektprüfung vor dem Lesen) | geprüft, ohne Befund |
| `testserver/TableStore.kt`, `RowRules.kt` (`update`: Filter, Wertprüfung, Eindeutigkeit, ganz oder gar nicht) | geprüft, ohne Befund (Nebenläufigkeit F-8; `@Synchronized`, Prüfung und Schreiben je Aufruf atomar) |
| Öffentliche Fläche (`SPEC-059`, [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md)) | `make flaeche` grün; `Statement.Merge`, `MergeParts`, `SimulationMerges`, `MergeExecution`, `RowRules` sind `internal`; kein neuer öffentlicher Name; geprüft, ohne Befund |
| Handle-Grenze ([ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md)) | MERGE geht über `sp_executesql`, `sp_prepexec`, `sp_execute` durch dieselbe Abbildung; 2500 Zeilen je Import ohne Abweisung (Lauf G1/G2); geprüft, ohne Befund |
| `tools/jdbc-messung`, `tools/vergleichslauf-d.sh`, `tools/mk/vergleichslauf-d.mk` | Docker-only (JDK-Image per Digest, kein Host-Aufruf), Digest von d-migrate und JDK gepinnt, Redaktion geprüft; geprüft, ohne Befund (F-13) |
| `docs/messungen`, Ablage `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate` | Stichprobe gegen die Aufnahme und gegen die Wiederholung beider Instanzen; geprüft, ohne Befund außer F-2 |
| Hard Rules `AGENTS.md` §3.1, §3.2, §3.4, §3.5, §3.6 | kein Host-Aufruf, kein `@Suppress` im Diff, `spec/architecture.md` unberührt, keine ADR geändert, keine Gate-Lockerung (`config/`, `Dockerfile`, `build.gradle.kts` unberührt); geprüft, ohne Befund |
| `AGENTS.md` §3.3 (Moves) | `868e4839` und `42900e4a` sind reine Moves (R100), Verweis-Nachzug in eigenen Commits; geprüft, ohne Befund |
| `AGENTS.md` §3.7 (Kommentare) | die neuen KDoc und Skript-Köpfe tragen Zusage, Kopplung oder Grenze; kein Konjunktiv über verworfene Alternativen im Produktcode; geprüft, ohne Befund |
| Haken (Reviewer-Skill „Haken vor der Verifikation gesetzt“) | im Slice kein `- [x]`, Closure-Notiz §7 leer; geprüft, ohne Befund |
| Commit-Nachrichten | jede nennt `TDS-*` oder `ADR-*`; geprüft, ohne Befund |
| Sicherheit ([TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)) | Ablage der Aufnahme und Meldungen des Backends ohne Kennwort und ohne Werte (feste Meldungen „Zeilenquelle fehlgeschlagen“, „Änderung fehlgeschlagen“); geprüft, ohne Befund |

## Summary

Fünf MEDIUM, vier LOW, fünf INFO, kein HIGH. Der Kern (Zerlegung, Schlüsselprüfung, `$action`, Verengung) hält gegen die Einzelmutationen und gegen die Läufe von d-migrate am Testserver; die Messung am echten Server ist im Repo wiederholbar und wiederholt. Das Gewicht liegt bei F-1/F-2: das `MERGE` von d-migrate scheitert an Tabellen mit DECIMAL-, TIME- und DATETIME2-Spalten (auch an der mitgelieferten Beispiel-Tabelle `bestellungen`), die geplante Änderung an `SPEC-030` ist nicht geliefert und ohne Träger, und Doku wie Spec nennen die Bedingung nicht. Das gehört vor `slice-abnahme-d-migrate`. F-3 verlangt Träger für die drei benannten Zielbild-Abweichungen, F-4 einen Test für die Abbruch-Prüfung, F-5 die Tabelle §3. Haken setzt der Planner nach Verifikation; Konflikte mit `worktree-agent-a9764e63162fbe637` nur in `docs/user/testserver-manuell.md` (F-12).
