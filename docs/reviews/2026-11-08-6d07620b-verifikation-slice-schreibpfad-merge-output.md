# Verifikationsbericht: slice-schreibpfad-merge-output (HEAD 6d07620b)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-schreibpfad-merge-output` (`in-progress/`, Zweig `worktree-agent-a367dd0d063325ded`, HEAD `6d07620b`, enthält `main` bis `c8aa1847`; Slice-Anteil gegen die Basis `f5250140`). Verlauf: erster Review `2026-11-07-b2ff295c-review-slice-schreibpfad-merge-output.md` (F-1 bis F-14), Re-Review `2026-11-07-f7f9ff33-re-review-slice-schreibpfad-merge-output.md` (R-1 bis R-10), Nachbesserung `963a0474` (R-1 bis R-5, R-7 bis R-10), Merge `6aa9410e`, `0f5031ba` (R-6, Messreihe), `6d07620b`. Die Nachbesserung ab dem Re-Review hat keinen zweiten Review (Regel `BEO-TDS/nachbesserung-ohne-zweiten-review`); ich bewerte `f7f9ff33..HEAD` unten wie ein Reviewer.
* Maßstab: DoD §2, Plan §3, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), `SPEC-030`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), `AGENTS.md` §3.1 bis §3.8.
* Grundsatz des Nutzers: „Spec ist Zielbild“, der echte SQL Server ist die Referenz; Abweichungen sichtbar mit Träger.
* Verifiziert wird die Zusage, nicht der Diff; Belege sind eigene Läufe. Nichts am Produktivcode geändert. Der Arbeitsbaum war vor diesem Bericht sauber; der Lauf `jdbc-*` schreibt die beiden Ablagedateien unter `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/` neu, ich habe sie nach dem Vergleich mit `git checkout` zurückgesetzt. Mutationen liefen in Wegwerf-Kopien (`git archive HEAD`) im Scratchpad.

## Eigene Läufe (Belege)

Host: Lastmittel 7 bis 54 durch fremde Läufe (kein ruhiger Host); Einzelmessungen. Maschine wie im Messprotokoll (Linux 6.8, 20 Kerne, Docker).

| Lauf | Ergebnis |
|---|---|
| `make gates FORCE=1` | Exit 0 in 32 s. **Cache-Falle:** die Docker-Stufen kamen aus dem Layer-Cache (identische Quellen wie der Lauf des Implementers); der Lauf beweist die Verdrahtung der Gates, nicht einen frischen Testlauf. Den Frischbeleg liefern die beiden nächsten Zeilen |
| `make clean-build` (alle drei Stufen `--no-cache`: `build-jar`, `test`, `detekt`) | Exit 0, `:test` + `:testserver:test` + `integratorTest` 1230 `PASSED`, 0 `FAILED`; 12 Minuten bei Last 28 bis 31 |
| `make flaeche` | Exit 0 |
| `make docs-check` | Exit 0, 1237 Dateien, 0 Befunde (vor diesem Bericht); mit diesem Bericht 1238 Dateien, 0 Befunde |
| `make matrix-zitate` | Exit 0, 1230 bestandene Testfälle, 309 Zitate, 0 ohne PASSED |
| `make spec-abdeckung` | Exit 0, „unverändert“, aber **aus dem Layer-Cache** (9 s). Frischlauf (`docker build --target test-bericht-export --no-cache-filter test-bericht`, dann `--target spec-abdeckung-export --no-cache-filter spec-abdeckung`, Ausgabe im Scratchpad): 1230 `PASSED`, 0 rot, die erzeugte Matrix ist **byte-gleich** der eingecheckten (`cmp` Exit 0), 6 Festlegungen ohne Nachweis (wie im Lauf des Targets) |
| `make e2e` | Exit 0, 8 Stufen grün, 157 s (Stufe 7: 41 s, Last 18 bis 25) |
| `make kompatibilitaet KATEGORIE=Synchronitaet` ×8 (R-6) | 8 von 8 grün (`Cancel_und_Command_Timeout_kurz_hintereinander…` je bestanden), Last beim Start 15 bis 55, gleichzeitig liefen vier und drei Gradle-Mutationsbauten. Mit der Stufe 7 des `make e2e` zusammen 9 von 9 grün auf `6d07620b` |
| `make vergleichslauf-d LAUF=jdbc-2022` und `jdbc-2025` (EULA bestätigt, lokal) | beide Exit 0 (120 s und 148 s einschließlich Bau, Last 20 bis 50). Die 19 Schritte haben die Exit-Codes der Ablage (nur `bulk-int` Exit 5, wie am Server gemessen). **Parameter-Bytes** je Typ und Wert (32 verschiedene Paare aus Typ, Wert und `hex=`: `DECIMALN` `6A 11 26 02 …` mit 3, 2, 6 Byte Wert, NULL und `01 00` für 0,00; `DATETIMN` `6F 08 08 DF 63 00 00 B4 25 DA 00`, `…FF 81 8B 01`, `…00 00 00 00`, NULL `6F 08 00`; `DATETIME2N` `2A 07 08 …`; `BITN`; `DATEN`; `INTN` 8 Byte) sind in beiden Instanzen **gleich der Ablage** (Vergleich der Mengen vor und nach dem Lauf, kein Unterschied). Der Export von `zeiten` zeigt am echten Server in Zeile 4 `"Uhr": "23:59:59"` für `23:59:59.997` (Instanz 2022 und 2025) |
| d-migrate (Digest `sha256:308f27753036…`) gegen `make testserver-up` aus dem Arbeitsbaum (Abbild `sha256:ca9b36a3194e…`, zwei Container auf 1451/1452, Beschreibung mit `kunden`, `kunden_int` (INT-Schlüssel), `bestellungen`, `zeiten`) | S1 `import --on-conflict skip` 3 eingefügt, Exit 0; S2 `skip` „1 inserted, 2 skipped“; S3 `update` „3 updated“, Export `Ada geändert`, Zeile 4 `Dora`; S4/S5 `data transfer` `kunden` skip/update Exit 0; S6/S7 `kunden_int` (INT) skip „3 inserted“, update „1 inserted, 2 updated“, Exit 0; S8 Standardpfad auf `kunden_int` Exit 5 „converting the Long value to JDBC data type INTEGER“ (wie am echten Server); S9 `bestellungen` skip „5 inserted“, update „5 updated“, Export `"12.50"`, NULL, `"-0.05"`, `"99999999.99"`, `"0.00"`; S10 `zeiten` skip „4 inserted“, update „4 updated“, Export alle vier Zeilen, Zeile 4 `"Uhr": "23:59:59"`; S11/S12 `data transfer bestellungen,zeiten` skip/update Exit 0, Export von B gleich A; S13 Standardpfad auf vorhandene Zeilen Exit 5 „Violation of PRIMARY KEY“. **Drei gleichzeitige `skip`-Importe** derselben drei neuen Schlüssel: alle drei Exit 0 („3 inserted“, „0 inserted“, „0 inserted“), der Export zeigt jeden Schlüssel genau einmal |
| Mutationen (7 einzeln, siehe unten) | alle 7 rot, in den erwarteten Tests |

Befehle und Ausgaben liegen im Scratchpad (`verif/`, `verif8/` mit `dm/lauf.log`); sie sind nicht im Repo.

## Gesamturteil

**Closure-fähig: nein, solange die Bedingungen 1 bis 4 offen sind.** Der Kern trägt und ist in eigenen Läufen belegt: Gates und Frischlauf der Tests grün, die Parameter-Bytes und die Läufe von d-migrate am echten SQL Server (2022 und 2025) und am Testserver stimmen mit der Ablage und dem Messprotokoll überein, die sieben Mutationen röten die Tests, die das Protokoll nennt, und die Behebungen R-1 bis R-5 und R-7 bis R-10 sind am Dokument und am Test nachvollziehbar. Offen sind keine Neubauten, sondern der **Träger der Zielbild-Abweichungen**, der beim Merge mit dem inzwischen vorgerückten `main` kollidiert und dort nicht trägt (V-1), und Fortschreibungen (Register, Änderungsprotokoll, Kommentar in `vergleichslauf-d.mk`). 0 HIGH, 1 MEDIUM, 4 LOW, 4 INFO.

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Bibliothek, Form und Weitergabe, Messauftrag | **erfüllt mit V-1** | Form M, Weitergabe, `$action`, Zähler; gemessen am echten Server mit mssql-jdbc (M-25 bis M-32), von mir mit `jdbc-2022`/`jdbc-2025` wiederholt (Parameter-Bytes gleich). Nicht gebaut und benannt: `$action` als `nvarchar(10)`, Abschluss `0x0117`, Fehlernummern 2628/2627/208 (Träger siehe V-1) |
| 2 | Testserver | erfüllt | `TableStore.onUpdate`, `RowRules`; S1 bis S13 und die drei gleichzeitigen Importe in eigenem Lauf; `TableStoreTest` grün |
| 3 | Beleg am fremden Client | erfüllt | S1 bis S13 oben, Rechner und Last und Abbild-Digest in diesem Bericht; das Messprotokoll (`docs/messungen/merge-output-2026-11-07.md`) nennt als Stand „Arbeitsbaum, nicht committet“ (V-5), mein Lauf steht auf `6d07620b` |
| N1 | `make gates` grün | erfüllt | `FORCE=1` Exit 0 (aus dem Cache), `make clean-build` Exit 0 frisch, Frischlauf der Tests über die Stage `spec-abdeckung` 1230 bestanden |
| N2 | Review liegt vor | **teilweise** | erster Review und Re-Review ja; die Nachbesserung `963a0474` bis `6d07620b` hat keinen Review, ersatzweise bewertet unten (V-2 bis V-9) |
| N3 | Doku-Update bei Vertrag | **teilweise** | Spezifikation, Nutzer-Doku, Matrix, Messprotokoll fortgeschrieben (R-1 bis R-3 behoben); Änderungsprotokoll der Spezifikation unvollständig (V-4), Kommentar im Kopf von `vergleichslauf-d.mk` stale (V-3) |
| N4 | Closure-Notiz | offen, erwartet | §7 leer, Sache der Closure |
| N5 | Register fortgeschrieben | **teilweise** | `wanduhr-tests-flackern-unter-last` mit Evidence fortgeschrieben; `messung-am-echten-sql-server-fehlt` nicht (V-2) |
| N6 | Risiken aus §6 mit Ausgang | offen, erwartet | acht Risiken, alle „offen bis zur Closure“; Vorschlag in Bedingung 3 |
| N7 | Drei Paarungen | offen bis Welle-Closure | |
| — | **Haken** | **nicht gesetzt (richtig)** | `grep '\[x\]'` im Slice: 0 Treffer, auch nicht im Diff gegen `f5250140`; `BEO-TDS/haken-vor-verifikation-gesetzt` nicht verletzt. Die Haken setzt der Planner |

## Plan-vs-Code-Diff (§3)

Abgleich der Dateien der Slice-Commits (43 Dateien ohne `docs/reviews`) gegen die Tabelle §3: jede Datei hat eine Zeile, entweder namentlich (`MergeParts`, `SimulationMerges`, `SimulationBackendMergeTest`, `TableStoreTest` ohne Endung) oder über ihr Verzeichnis (die beiden `…aufnahme.txt` unter `jdbc-d-migrate/`). Die Zeilen R-4 (Referenztest, Matrix, `RpcParams`, drei Rpc-Testklassen, `M-25 bis M-32`) stehen nun drin. Nicht in der Tabelle, im Diff: die Beobachtung `wanduhr-tests-flackern-unter-last` (Evidence und Stand, Register) und die Pfadanpassungen anderer Planungsdateien; beides ist Register-Pflege, kein Mangel. `make flaeche` grün, kein neuer öffentlicher Name (`SPEC-059`); die Änderung an `SimulationBackend` ist intern. Die Tabelle trägt die Abweichung „Plan nicht vor Code“ ehrlich ausgewiesen. Urteil: vollständig.

## Behebungen R-1 bis R-10 (nachvollzogen)

| Finding | Ergebnis |
|---|---|
| R-1 `DATETIMN` in Spec und e2e noch „abgelehnt“ | behoben: Spezifikation (RPC-Abschnitt Backend-Vertrag) nennt `DATETIMN` mit 4 Byte und die neuen `VALUE`-Gründe; `tools/e2e.sh` und die erzeugte Abdeckung sagen „DATETIMN mit 4 Byte … abgewiesen, mit 8 Byte gelesen und beantwortet“. Grep `DATETIMN` über `spec/`, `docs/plan/`, `tools/` ohne Rest |
| R-2 `testserver-manuell.md` | behoben: „Grenzen“, „Zeilen kommen über …“ und die Liste der erkannten Formen nennen `MERGE`/Form M; gegen Lauf S1 bis S13 gelesen |
| R-3 `einbettung-und-betrieb.md` | behoben: Form M in „Was das Backend erkennt“, `primaryKey`-Satz, Callback-Satz und eine Eigenschaft zu Sperre, Nicht-Reentranz, hängendem Callback; der Satz „Ob ein Abbruch am Draht mitten im Warten auf die Sperre wirkt, ist nicht getestet“ bleibt als Grenze stehen |
| R-4 Plan-Tabelle | behoben (siehe Plan-vs-Code-Diff) |
| R-5 „gemessene“ Vektoren | behoben: der Test „gemessene Vektoren“ enthält nur Bytes der Aufnahme (`…00 00 00 00`, `…FF 81 8B 01`, NULL stehen in `2022.aufnahme.txt` und `2025.aufnahme.txt`, von mir gegen den frischen Lauf verglichen); die Grenzwerte 1900-01-01, letzter Tick am Tag 0 und 0001-01-01 stehen in einem eigenen Test „nach der Lesart von MS-TDS, nicht am echten Server aufgenommen“; Matrixzeile trennt beide. Die Rundung zu Millisekunden ist durch den gemessenen Vektor `…FF 81 8B 01` (ergibt `…997`) gedeckt: Mutation mC (Abschneiden) röten beide Tests |
| R-6 e2e Stufe 7 | nicht behoben, zu Recht als Beobachtung geführt; Einordnung V-6 |
| R-7 Wartefall, Nicht-Reentranz, hängender Callback, `stopped` im Lesen | behoben im Bibliotheks-Test: drei neue Tests; Mutation m3 jetzt rot (siehe Mutationen). **Nicht behoben:** Abbruch am Draht während des Wartens auf die Sperre, offen benannt |
| R-8 Tabelle N15 bis N22, Gestalten | behoben: Messprotokoll-Tabelle zusammenhängend N1 bis N24; die Gestalten-Tests prüfen nun den Grund der Ablehnung in der Meldung |
| R-9 `time`-Bruchteile | als Grenze in `SPEC-036` (Form M) und im Messprotokoll (S10, M-32) benannt; am echten Server gemessen, dass der Export `23:59:59` zeigt, nicht, was die Spalte speichert. Träger: nur die Spezifikation (V-7) |
| R-10 Wert 0,00 als `DECIMALN` Länge 2 | Text korrigiert (`SPEC-030`, M-31: „beim `data transfer` als `DECIMALN` mit Länge 2 (`01 00`)“), von mir in beiden Instanzen am Draht gefunden (`6A 11 26 02 02 01 00` in der Ablage); es gibt weiter keinen eigenen Test-Vektor dafür (V-8) |

## Mutationen (selbst gefahren)

Wegwerf-Kopien von `6d07620b`, je eine Mutation, `docker build --target test` mit gefilterten Tests (`:test` auf `SimulationBackendMergeTest`, `SimValuesTest`, `RpcBinderTest`, `RpcTypesTest`, `RpcFailureTest`, `:testserver:test` auf `TableStoreTest`, `integratorTest` auf `MergeEmbeddingTest`, `--continue`). Sie liefen zu viert gleichzeitig, jede in eigener Kopie.

| Nr. | Mutation | Rot (Anzahl der roten von 80 gelaufenen Tests im Gradle-Task `:test`) |
|---|---|---|
| mA | Sperre entfernt (`lock.withLock` durch `run`) | 3: „zwei gleichzeitige MERGE desselben Schluessels laufen nacheinander“, „ein haengender Callback haelt die Sperre fuer weitere MERGE“, „ein Callback, der dasselbe Backend aufruft und abwartet, kommt nicht zurueck“ |
| mB | `\|\| stopped.get()` vor dem Callback entfernt | 2: „ein Abbruch zwischen Lesen und Callback schreibt nichts“, „ein Abbruch beendet das Lesen vor der naechsten Zeile und vor dem Oeffnen“ |
| mG | beide `stopped`-Prüfungen in `exists()` entfernt (m3 des Re-Reviews) | **1**, genau „ein Abbruch beendet das Lesen vor der naechsten Zeile und vor dem Oeffnen“ (der Re-Review fand sie ohne roten Test; behoben) |
| mC | Rundung der Ticks abgeschnitten (`+ 1` entfällt) | 2: `RpcBinderTest` „Dezimalwerte kurzer Laenge und datetime sind gemessene Vektoren“ und „datetime-Grenzwerte nach der Lesart von MS-TDS …“ |
| mD | `DATETIMN` jeder Größe als Zeitpunkt | 1: `RpcBinderTest` „Typen ausserhalb des Mindestkatalogs sind abgelehnt“ |
| mE | Prüfung „Betrag ≥ 10 hoch Precision“ entfernt | 2: `RpcBinderTest` „datetime und kurzer Dezimalwert ausserhalb des Bereichs sind abgelehnt“ und „Dezimalwert ausserhalb des Bereichs ist abgelehnt“ |
| mF | Zeilenquelle nicht geschlossen | 2: „fehlender Schluessel ruft onInsert und antwortet INSERT“, „vorhandener Schluessel ohne Update-Zweig ruft nichts und antwortet ohne Zeile“ |

Mit mB/mG ist die Aussage des Messprotokolls N23/N24 („gemeinsam angewandt, nicht einzeln getrennt“) einzeln bestätigt: der Abbruch-Lesetest deckt mG allein, mB wird von zwei Tests gedeckt.

## Findings (Reviewer-Bewertung von `f7f9ff33..HEAD` und Verifikationsbefunde)

### V-1 MEDIUM — Träger der drei Zielbild-Abweichungen kollidiert mit `main` und trägt dort nicht

* kategorie: MEDIUM
* quelle: Grundsatz „Spec ist Zielbild“ (Abweichungen sichtbar mit Träger, der die Closure überlebt); `AGENTS.md` §3.8; DoD-Punkt 1; Auftrag Punkt 5
* pfad: `docs/plan/planning/in-progress/slice-antwort-tokens-zielbild-luecken-bauen.md` · §6 Risiko „Aus slice-schreibpfad-merge-output: Länge der Spalte `$action` …“
* befund: Der Slice trägt die drei nicht gebauten Abweichungen (`$action` `nvarchar(10)`, Abschluss `0x0117`, Fehlernummern 2628/2627/208) mit **einer Risikozeile** in diesen Träger-Slice ein. `main` (jetzt `d18a8541`, Neuschnitt nach [ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md)/[ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md), beide `Accepted`) hat diese Datei umgeschrieben: `git merge-tree` main gegen den Zweig meldet **genau einen Konflikt, in dieser Datei**. Auf `main` steht keine der drei Abweichungen: `nvarchar(10)`, 2628 und 2627 kommen in keinem Slice von `open/` vor (nur im Slice selbst), und für `0x0117` steht dort nur „`curCmd` weiterer Anweisungsarten (MERGE `0x0117`, SELECT): nur dort gebaut, wo W-6 und M-13 messen; sonst `UNKNOWN`“ — `StatementKind` hat dort kein Mitglied `MERGE`, `0x0117` ist aber in M-26 gemessen. Eine gemessene Abweichung ohne Lieferpunkt in einem Slice ist kein Träger. Auch in der Fassung des Zweigs ist es nur ein Risiko mit „Ausgang: offen bis zur Closure“, kein Lieferpunkt. Der Eintrag `$action` als Feld an einem öffentlichen Typ (`ResultColumn`) hängt zudem weiter an einem Entscheid des Nutzers ([ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4), der nicht eingeholt ist ([ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) entscheidet die Katalogspalten, nicht `$action`).
* verifizierbar: ja (`git merge-tree --write-tree main <Zweig>`; `git grep -n -F '2628' main -- docs/plan/planning/open`)
* klasse: Träger fehlt oder stirbt beim Merge

### V-2 LOW — Register `messung-am-echten-sql-server-fehlt` nicht fortgeschrieben

* kategorie: LOW
* quelle: DoD „Beobachtungs-Register fortgeschrieben“; `AGENTS.md` §3.7 (ein Zustandsfeld nennt den Zustand)
* pfad: `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md` · „Folgepflicht 2 von [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) … werden … nachgemessen; … Bis dahin trägt die Messreihe nur den Referenzclient“
* befund: Der Stand führt als gemessen M-1 bis M-24; M-25 bis M-32 (JDBC-Client, mssql-jdbc 13.4.0) fehlen, die Folgepflicht 2 steht weiter im Futur („werden … nachgemessen“, „Bis dahin“), obwohl sie in diesem Slice erfüllt ist (INT-Spalten im Standardpfad: S8, M-28). Eine Evidence-Datei für diesen Slice gibt es nur beim Register `wanduhr-tests-flackern-unter-last`. Die Änderung an dem Register ist die Pfadanpassung `open/` zu `in-progress/`.
* verifizierbar: ja (Grep `M-25` in `state.md`: kein Treffer)
* klasse: Fortschreibung fehlt, Zustandsfeld stale

### V-3 LOW — `tools/mk/vergleichslauf-d.mk`: „zwoelf Befehle“ und Laufzeit gelten für den Lauf `jdbc` nicht mehr

* kategorie: LOW
* quelle: `AGENTS.md` §3.7, §3.8; Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen“
* pfad: `tools/mk/vergleichslauf-d.mk` · „zwoelf Befehle (`data import` und `data transfer` … ) … beide Instanzen zusammen 188 s einschliesslich Bau und Start (Lastmittel 17)“
* befund: Der Lauf führt 19 Befehle (`tools/vergleichslauf-d.sh`, Funktion `jdbclauf`, die Zeilen `schritt …`) und `docs/messungen/referenzabgleich.md` sagt „neunzehn Befehle“; der Kopf der mk-Datei nennt zwölf und eine Laufzeit von 188 s, die für den Umfang vor `merge-decimal-*`, `merge-zeit-*` und `transfer-decimal-zeit` gemessen wurde. Meine Läufe: 120 s und 148 s je Instanz (Last 20 bis 50), zusammen 268 s.
* verifizierbar: ja
* klasse: Fortschreibung lässt zweite Fundstelle stehen

### V-4 LOW — Änderungsprotokoll der Spezifikation nennt `SPEC-030` (`DECIMALN`, `DATETIMN`), die Sperre und M-31/M-32 nicht

* kategorie: LOW
* quelle: Lastenheft-Spec-Konvention (Änderungsprotokoll); `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · Änderungsprotokoll, Zeile „2026-11-07 | `MERGE … OUTPUT $action` des Simulations-Backends …“
* befund: Die Zeile bleibt bei „Messprotokoll der Referenzabgleiche M-25 bis M-30“ und „offen: Länge von `$action` und Befehl `0x0117`“. Seit `6d489e14` ändert der Slice `SPEC-030` (`DECIMALN` mit kurzem Wert, `DATETIMN` mit 8 Byte als Zeitpunkt, mit Ablehnungsgründen), `SPEC-036` (Sperre je Backend, Nicht-Reentranz, Parametertypen) und stützt sich auf M-31 und M-32; die Fehlernummern 2628/2627/208 stehen im „offen“ nicht. Der Text der Spezifikation selbst ist vollständig (Abschnitte `SPEC-030`, `SPEC-036`); nur das Protokoll nennt es nicht.
* verifizierbar: ja (Grep `DECIMALN` hinter der Zeile 1895 der Datei: kein Treffer im Protokoll)
* klasse: Änderungsprotokoll unvollständig

### V-5 LOW — Das Messprotokoll der d-migrate-Läufe belegt einen nicht committeten Stand; die Ablage ist nicht byte-reproduzierbar

* kategorie: LOW
* quelle: DoD-Punkt 3 „Ausgabe mit Rechner, Last und Image-Digest“; `AGENTS.md` §3.8
* pfad: `docs/messungen/merge-output-2026-11-07.md` · Bedingungen, „Testserver“: „Zweig des Slice auf `478c4332` mit den Änderungen der Nachbesserung im Arbeitsbaum (nicht committet zur Zeit des Laufs)“; `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/*.aufnahme.txt`
* befund: Die Läufe S1 bis S13 sind an einem Abbild gemessen, das kein Commit hat; zwei Abbilder tragen zwei Gruppen von Läufen. Ich habe alle 13 Läufe sowie die drei gleichzeitigen Importe auf `6d07620b` (Abbild `sha256:ca9b36a3194e…`) wiederholt, mit gleichem Ergebnis; das ersetzt den Beleg, ändert aber nicht den Wortlaut des Protokolls. Die Ablage der Aufnahme ist bei Wiederholung nicht byte-gleich: Lauf-UUIDs und Zeiten ändern sich, und die Zeilen der beiden Richtungen (`C->S`, `S->C`) stehen wegen der Nebenläufigkeit des Durchlaufs in anderer Reihenfolge; der Nachbesserungs-Commit `963a0474` ändert deshalb 2996 und 517 Zeilen, ohne dass sich ein Byte der Parameter oder ein Exit-Code ändert (von mir mit Mengenvergleich der Paare Typ, Wert und `hex=` und der Exit-Codes bestätigt). Wer die Ablage diffen will, findet das Rauschen größer als die Änderung.
* verifizierbar: ja
* klasse: Beleg-Stand nicht Commit; Ablage nicht deterministisch

### V-6 INFO — R-6 (Stufe 7): nicht reproduziert, Zuordnung weiter weder belegt noch widerlegt

* kategorie: INFO
* quelle: [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität); Auftrag Punkt 1
* pfad: `referenztest-dotnet/Kompatibilitaet/SynchronitaetTests.cs` · „Cancel_und_Command_Timeout_kurz_hintereinander_lassen_die_Folgeabfrage_nur_ihre_Antwort_erhalten“
* befund: Auf `6d07620b` 9 von 9 grün (Stufe 7 des `make e2e` bei Last 18 bis 25; acht Wiederholungen bei Last 15 bis 55, davon fünf über Last 30). Die Messreihe des Implementers (Evidence `slice-schreibpfad-merge-output.md`): `b2ff295c` 15 von 15 grün, Zweig 13 von 15 grün bei künstlicher Last 41 bis 94; der Unterschied 0 gegen 2 von 15 hat bei dieser Zahl p≈0,24 (exakter Test nach Fisher, einseitig; Rechnung von mir) und trägt keinen Schluss. Zusammen auf dem Zweig: 2 rote von etwa 24 gemessenen Läufen (die Läufe des Re-Reviews nicht gezählt). Der Implementer ordnet es als Setzung ein („kein Pfad mit `MERGE`“), das ist ehrlich benannt. Die Beobachtung steht mit Evidence im Register (4×, Ausgang beim Nutzer). Eine Wirkung der Nachbesserung ist nicht auszuschließen (der Re-Review und die Messreihe sagen es selbst), aber auch nicht belegt; kein Bedingungspunkt der Closure.
* verifizierbar: ja
* klasse: Flake nicht zugeordnet

### V-7 INFO — Träger der Grenze „Bruchteile in einer `time`-Spalte“ ist nur die Spezifikation

* kategorie: INFO
* quelle: Grundsatz „Abweichungen sichtbar mit Träger“; `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · `SPEC-036` Form M, „Nicht gemessen: … Bruchteile einer Sekunde in einer `time`-Spalte“
* befund: Gemessen (von mir in beiden Instanzen wiederholt) ist, dass der Export von d-migrate `23:59:59` für `23:59:59.997` zeigt, am echten Server und am Testserver gleich. Nicht gemessen ist, was die Spalte speichert (`time(7)` am echten Server; die Bibliothek verengt eine `datetime`-Zeit in eine `Time`-Spalte). Die Grenze steht in der Spezifikation und im Messprotokoll, in keinem Slice und in keinem Register; das Risiko wird bei der Closure nirgends abgeholt. Das ist als benannte Spec-Lücke zulässig (Baseline: Spec-Lücke ohne `liegt in`), muss aber als solche in die Closure-Notiz.
* verifizierbar: ja
* klasse: Grenze ohne Träger außer Spec

### V-8 INFO — Für `DECIMALN` mit dem Wert 0,00 (Länge 2, `01 00`) gibt es keinen eigenen Test-Vektor

* kategorie: INFO
* quelle: `AGENTS.md` §3.8 (R-10)
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcBinderTest.kt`
* befund: R-10 ist durch Berichtigung des Textes behoben; der gemessene Fall (`6A 11 26 02 02 01 00` in der Ablage beider Instanzen, mit dem Lauf `transfer-decimal-zeit` von mir wiederholt, Exit 0) steht ohne Test-Vektor. Der Leser ist generisch (Vorzeichen 1, Betrag 0); eine Mutation, die genau diesen Fall bräche, ist nicht erdacht.
* verifizierbar: ja
* klasse: Messfall ohne Vektor

### V-9 INFO — Übergabepunkt: `MERGE` gegen eine Tabelle ohne Schlüssel

* kategorie: INFO
* quelle: Auftrag Punkt 7
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimulationMerges.kt` · `comparesKey`
* befund: Der Katalog-Zweig (und der Neuschnitt `slice-katalog-und-struktur-an-messung-angleichen` auf `main`) lässt `CREATE TABLE` ohne Schlüssel gelingen (M-19). `comparesKey` verlangt, dass der Vergleich genau den Schlüssel nennt; bei leerem Schlüssel gilt das nie, das `MERGE` endet mit `TDS_UNSUPPORTED_SQL`, Meldung „MERGE vergleicht nicht genau den Schlüssel“. Das ist definiert und sicher, am echten Server ist ein `MERGE` ohne Schlüssel möglich; kein Slice nennt den Fall.
* verifizierbar: ja (Lesen von `comparesKey`)
* klasse: Wechselwirkung zweier Zweige

Negativbefunde: Haken (siehe oben, keine gesetzt); `make flaeche`; Parameter-Bytes (gleich der Ablage); Exit-Codes aller 19 Schritte (gleich); `Plan §3` (vollständig); Frischlauf der Tests (1230 bestanden); die Matrix `docs/plan/spec-abdeckung.md` (frisch erzeugt gleich der eingecheckten); `AGENTS.md` §3.1 (nur `make`/Docker), §3.2 (kein `@Suppress` im Diff: Grep ohne Treffer in den Slice-Dateien von `src/` und `testserver/`), §3.5 (keine ADR geändert). Lauf `make e2e` ohne Befund.

## Konflikte mit `main` und den anderen Zweigen (Übergabepunkte für den Merge)

Prüfung mit `git merge-tree --write-tree` (Stand der Zweige am 2026-11-08) und Diff der gemeinsamen Dateien. `main` ist inzwischen bei `d18a8541`; gegenüber `c8aa1847` (enthalten im Zweig) ändert `main` nur Dokumente ([ADR-0041](../plan/adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md), [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md), Planung, Register), **kein `src`, kein `testserver`**; die getesteten Quelltexte sind damit die des Merges.

| Gegenstand | Konflikt | Übergabepunkt |
|---|---|---|
| `main` (`d18a8541`) | **ein** Textkonflikt: `docs/plan/planning/in-progress/slice-antwort-tokens-zielbild-luecken-bauen.md` (die Zeile des Zweigs in §6 gegen die Neufassung von `main`). Ohne Konflikt, aber mit Pflicht: `main` hält `slice-schreibpfad-merge-output.md` in `open/`, der Zweig in `in-progress/`; `git mv`/Roadmap sind im Zweig schon ausgeführt | V-1: beim Merge die drei Abweichungen in die Fassung von `main` übernehmen und `0x0117` (MERGE) mit Mitglied oder Ausschluss benennen |
| `bulk-load-feste-typen` (`worktree-agent-a8329d681f4d810e9`) | Textkonflikte nur in Dokumenten: `spec/spezifikation.md` (2 Blöcke: `SPEC-036` Form B und Form M nebeneinander), `docs/user/einbettung-und-betrieb.md` (1), `docs/user/testserver-manuell.md` (2), außerdem Planung/Roadmap/Welle, `spec-abdeckung.md`, `harness/README.md`. **Kein Konflikt in `BulkDispatch.kt`, `RequestLoop.kt`, `SimulationBackend.kt`** (dort ändert der Zweig `catalog` von `private val` zu `internal val` und die Probe in `RequestLoop`; dieser Slice ändert beides nicht). `SimulationBackend.kt` ist beidseitig berührt, ohne Konflikt | `SPEC-036`: beide Formen (B mit festen Typen, M) bleiben stehen, der Satz „Nutzlast des Referenzclients wird nicht gelesen“ entfällt dort; `spec-abdeckung.md` nach dem Merge neu erzeugen (`make spec-abdeckung`); Nutzer-Doku: „Daten nur über das Massenladen und `MERGE`“ nicht auf eine der beiden Seiten verlieren |
| `sp-unprepare` (`worktree-agent-a7bc3c771ae8ca3bb`) | Textkonflikte nur in Dokumenten: `docs/messungen/referenzabgleich.md` (1), Roadmap, Register, `slice-antwort-tokens-zielbild-luecken-bauen.md`, ein Slice in `done/`. `ErrorAnswers`, `ErrorMapper`, `ResponseRun`, `UnprepareBatch` werden von diesem Slice nicht berührt | keine Übergabe im Code; `referenzabgleich.md`: M-22/M-23-Zeilen und M-25 bis M-32 nebeneinander halten |
| Katalog (`worktree-agent-a95b80c5a5d35df3e`, älterer Zuschnitt; `main` hat ihn inzwischen in `slice-katalog-und-struktur-an-messung-angleichen` und zwei weitere zerlegt) | Textkonflikte nur in Dokumenten (`einbettung-und-betrieb.md` (2), Roadmap, Register, `spec-abdeckung.md`). `StatementPlanner.kt`, `TableStore.kt`, `TableStoreTest.kt` sind beidseitig berührt und mergen **ohne** Konflikt; Zusammenspiel: der Katalog erlaubt `CREATE TABLE` ohne Schlüssel (`TableStore` prüft dann keine Eindeutigkeit), `MERGE` darauf endet mit `TDS_UNSUPPORTED_SQL` (V-9) | V-9: der Neuschnitt-Slice sollte den Fall benennen; nach dem Merge `make test` und `make spec-abdeckung` |

Nach dem Merge mit einem der Zweige ist `make gates` und `make clean-build` frisch zu fahren; das habe ich für keinen der drei Merge-Stände gefahren (nur die Textkonflikte geprüft), also bleibt die Übersetzbarkeit des Merges von `SimulationBackend.kt` mit dem Zweig `bulk-load-feste-typen` Annahme.

## Offene Risiken §6: Vorschlag der Ausgänge (setzt der Planner/Implementer bei der Closure)

| Risiko (Kurzform) | Vorschlag |
|---|---|
| JDBC-Messauftrag und EULA | entfallen: Messung M-25 bis M-32 liegt vor, von mir wiederholt |
| Aufwand der Sonde / Liste „abschließend“ | weiter offen (Setzung, Nutzer hat nicht bestätigt); Träger `SPEC-036` |
| Gestalt hängt an d-migrate 1.12.0 | weiter offen als benannte Grenze (Spezifikation und `vergleichslauf-d.sh`); nicht eingetreten |
| Konfliktsemantik und Callback-Entscheid | entfallen: ein Callback, gebaut und getestet |
| d-migrate schließt Anweisungsobjekte? | im Protokoll prüfen; sonst weiter offen mit Träger |
| Länge `$action`, `0x0117`, 2628/2627/208 | weiter offen, Träger **nach V-1** herstellen |
| Parametertyp des Clients | eingetreten und behoben (`Int64` in `Int32`, `DECIMALN`, `DATETIMN`), belegt |
| Stufe 7 des e2e | weiter offen, Träger `BEO-TDS/wanduhr-tests-flackern-unter-last` (V-6) |

## Bedingungen vor der Closure

1. **V-1:** Träger für `$action`/`nvarchar(10)`, Abschluss `0x0117` (MERGE) und die Fehlernummern 2628/2627/208 in der **Fassung von `main`** des Slice `slice-antwort-tokens-zielbild-luecken-bauen` (oder eines neuen Slice) als Lieferpunkt oder als Risiko mit auflösbarem Träger; Konflikt beim Merge in dieser Datei auflösen; Entscheid des Nutzers zu `ResultColumn` einholen oder als offen benennen.
2. **V-2, V-3, V-4:** Register `messung-am-echten-sql-server-fehlt` (M-25 bis M-32, Folgepflicht 2 erfüllt), Kopf von `tools/mk/vergleichslauf-d.mk` (19 Befehle, Laufzeit) und Änderungsprotokoll der Spezifikation (`SPEC-030`, Sperre, M-25 bis M-32) fortschreiben.
3. §6: jedem Risiko einen Ausgang geben (Vorschlag oben), §7 ausfüllen mit Steering-Loop-Eintrag; V-7 (`time`-Bruchteil) als benannte Spec-Lücke in §7.
4. Die Nachbesserung ab dem Re-Review hat keinen zweiten Review; dieser Bericht ersetzt ihn nicht, er ist die Verifikation. Entscheidung des Planners, ob ein Re-Review nötig ist (Regel `BEO-TDS/nachbesserung-ohne-zweiten-review`); ich habe keine HIGH-Befunde gefunden.
5. Haken setzt der Planner nach Erfüllung von 1 bis 3; der Implementer hat keine gesetzt.
6. Nach dem Merge mit `main`/den Zweigen: `make gates`, `make clean-build`, `make spec-abdeckung` (Matrix neu erzeugen, siehe Cache-Falle im Kopf von `tools/mk/spec-abdeckung.mk`).

## Spec-Lücken und Rückwege

* Spec-Lücke (Rückweg Verifikation zu Spec): `time`-Bruchteile (V-7) und der Abbruch am Draht im Warten auf die Sperre (R-7 Rest) sind benannt, nicht gemessen oder getestet.
* Kein Befund gegen Lastenheft oder ADR: die Zerlegung in Lesen und einen Callback, die Sperre als Setzung und die Form M entsprechen [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md); die Abweichungen vom Messbefund sind in der Spezifikation und im Plan benannt.
