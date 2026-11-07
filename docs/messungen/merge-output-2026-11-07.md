# MERGE mit d-migrate gegen den Testserver: Messprotokoll vom 2026-11-07

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) und [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (`AGENTS.md` §5 Regel 7), Träger: [slice-schreibpfad-merge-output](../plan/planning/in-progress/slice-schreibpfad-merge-output.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Es setzt das [Messprotokoll des Massenladens](bulk-load-2026-11-07.md) fort und stellt den **Testserver** neben die Messung am **echten SQL Server** (`M-25` bis `M-30` in [referenzabgleich](referenzabgleich.md)): dieselben Befehle von d-migrate, dieselbe Form des `MERGE`.

**Vorbehalt, der für jede Zeile gilt.** Gemessen ist das Verhalten von d-migrate 1.12.0 (mssql-jdbc 13.4.0) gegen den Testserver in dem Stand, der unten steht. Die Antworten des Testservers (Spalte `$action`, Zeilen `INSERT` und `UPDATE`, Zähler) folgen der Messung am echten Server; **zwei Merkmale der Antwort weichen ab** und sind nicht gebaut: die Spalte `$action` ist am echten Server `nvarchar(10)`, im Testserver `nvarchar(4000)`, und der Abschluss trägt am echten Server den Befehl `0x0117`, im Testserver `0x00c1` (Ursache: beides verlangte ein neues Feld an einem öffentlichen Typ, [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4). Ob d-migrate sie je auswertet, ist nicht gemessen: kein Lauf unten hängt an ihnen.

**Erzeuger der Zahlen.** Die Läufe unten fährt ein Wegwerf-Skript (Bash, nicht im Repo), das nur die Befehle der Tabelle ausführt, die Ausgabe von d-migrate und die Exportdateien sammelt; die Befehle, die Beschreibung der Tabellen und die Eingabedateien stehen vollständig in diesem Protokoll, die Läufe sind damit von Hand wiederholbar. Die Zeiten sind Einzelmessungen unter wechselnder Last und gelten als Größenordnung. Der Erzeuger der Messung am echten Server (`make vergleichslauf-d LAUF=jdbc`) liegt im Repo.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Host | 2026-11-07, Linux 6.8.0, 20 Kerne, 31 GiB, Docker 29.9.2; Last (`uptime`, 1/5/15 min) 12,31/15,58/14,71 zu Beginn des Laufs, durch fremde Läufe und einen gleichzeitigen Bau |
| Client | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, mssql-jdbc 13.4.0, `docker run --rm --network host`, URL `mssql://alice:secret@127.0.0.1:<Port>/manuell?encrypt=false` |
| Testserver | Zweig des Slice auf `22012212` mit den Änderungen des Slice im Arbeitsbaum (nicht committet zur Zeit des Laufs), `make testserver-up` (Stufe `testserver` des Dockerfiles), Abbild `sha256:000220d672bd69be5ac45507bc92be9c11cbdeaafa711a66bd19029cf97effa9`; zwei Testserver, A auf 14341 und B auf 14342, beide mit derselben Beschreibung (`TESTSERVER_BESCHREIBUNG`) |
| Beschreibung der Tabellen | `kunden` (`Id` biginteger Schlüssel, `Name` text 4000, `Eintritt` date) und `kunden_int` (`Id` integer Schlüssel, sonst gleich), beide leer; Namen, Typen und Schlüssel wie in `testserver/resources/manuell/sim.yaml`, die Länge von `Name` 4000 |
| Eingabe | `kunden.json`: `{"Id":1,"Name":"Ada Ünï","Eintritt":"2026-01-02"}`, `{"Id":2,"Name":null,"Eintritt":null}`, `{"Id":3,"Name":"Cäcilie","Eintritt":"2025-12-31"}`; `aenderung.json`: `{"Id":1,"Name":"Ada geändert","Eintritt":"2027-02-03"}`, `{"Id":3,"Name":null,"Eintritt":"2024-04-04"}`, `{"Id":4,"Name":"Dora","Eintritt":"2026-05-06"}` |

## Läufe

Alle Befehle mit `<A>` oder `<B>` als URL des jeweiligen Testservers, in dieser Reihenfolge am selben Bestand (jeder Lauf baut auf dem vorigen auf); Exportdatei jeweils `data export --source <URL> --format json --tables <Tabelle>`.

| Nr. | Befehl (gekürzt) | Exit | Ergebnis |
|---|---|---|---|
| S1 | `data import --target <A> --source kunden.json --table kunden --on-conflict skip` (Tabelle leer) | 0 | „3 rows processed, 3 inserted“; Export: die drei Zeilen der Eingabe, mit Umlauten und NULL |
| S2 | `data import … --source aenderung.json --table kunden --on-conflict skip` | 0 | „3 rows processed, 1 inserted, 2 skipped“; Export: Zeilen 1 und 3 unverändert (`Ada Ünï`, `Cäcilie`), Zeile 2 unverändert, Zeile 4 `Dora` neu |
| S3 | `data import … --source aenderung.json --table kunden --on-conflict update` | 0 | „3 rows processed, 3 updated“; Export: Zeile 1 `Ada geändert`/2027-02-03, Zeile 3 Name NULL/2024-04-04, Zeile 4 unverändert, Zeile 2 unverändert |
| S4 | `data transfer --source <A> --target <B> --tables kunden --on-conflict skip` (B leer) | 0 | „Transferred: kunden“; Export von B: vier Zeilen, gleich dem Export von A nach S3 |
| S5 | `data transfer … --on-conflict update` (alle vier Schlüssel in B vorhanden) | 0 | „Transferred: kunden“; Export von B unverändert gleich A |
| S6 | `data import --target <A> --source kunden.json --table kunden_int --on-conflict skip` (`Id` `INT`) | 0 | „3 inserted“ (der `bigint`-Parameter von d-migrate geht in die `Int32`-Spalte); Export: drei Zeilen |
| S7 | `data import … --source aenderung.json --table kunden_int --on-conflict update` | 0 | „3 rows processed, 1 inserted, 2 updated“; Export: Zeilen 1 und 3 geändert, Zeile 4 neu |
| S8 | `data import … --source kunden.json --table kunden_int` (Standardpfad, Massenladen) | 5 | „Import failed: An error occurred while converting the Long value to JDBC data type INTEGER.“ (im Treiber, wie am echten Server, `M-28`) |

**Vergleich mit dem echten SQL Server (`M-27`, `M-29`, Instanzen 2022 und 2025).** Gleich: `skip` auf einer vorhandenen Zeile ergibt „skipped“ und lässt die Zeile unverändert, `update` ändert, `data transfer` mit beiden Werten endet mit Exit 0 und gleichen Exporten, `INT`-Spalten gehen über `--on-conflict` (Exit 0) und scheitern im Standardpfad (Exit 5, gleicher Text). Nicht gleich und nicht verglichen: die Länge von `$action` und der Befehl des Abschlusses (siehe Vorbehalt), `ENVCHANGE` und `SESSIONSTATE` am Anfang der Antwort, der Fehlerfall des `MERGE` (Typüberlauf, doppelter Schlüssel in der Quelle; mit dem Referenzclient am echten Server `M-15`, mit mssql-jdbc nicht gemessen).

## Mutationsnachweise

Wegwerf-Kopie des Arbeitsbaums (Skript und Kopie nicht im Repo), 14 Mutationen auf einmal angewandt (`docker build --target test` mit `gradle --continue`), danach 2 Mutationen allein; jede Zeile nennt die Zusage, die Änderung am geprüften Code und die roten Tests. Bei der gemeinsamen Anwendung ist die Zuordnung zu einer Mutation aus den Assertions gelesen (zwei Mutationen mit gleichem roten Test sind nicht getrennt); N6 und N10 wurden zur Trennung allein gefahren. Ein weiterer roter Test dieses Laufs, `ListenerAbortTest > der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab`, ist ein Lastkandidat der Wanduhr-Tests ([BEO-TDS/wanduhr-tests-flackern-unter-last](../plan/planning/observations/BEO-TDS/wanduhr-tests-flackern-unter-last/observation.md)) und gehört zu keiner Mutation.

| Nr. | Zusage | Mutation | Rot |
|---|---|---|---|
| N1 | die Bibliothek prüft den Schlüssel der gelieferten Zeilen selbst | `matches` liefert immer wahr | `SimulationBackendMergeTest`: „die Bibliothek prueft den Schluessel der gelieferten Zeilen selbst“, „fehlender Schluessel ruft onInsert und antwortet INSERT“; `MergeEmbeddingTest` |
| N2 | NULL im Schlüssel trifft nie und öffnet die Quelle nicht | die Prüfung auf NULL im Schlüssel vor dem Öffnen entfällt | „NULL im Schluessel trifft nie und oeffnet die Zeilenquelle nicht“ |
| N3 | ohne Zweig `WHEN MATCHED` kein Callback und keine Zeile | der Fall „vorhanden“ wählt immer `UPDATE` | „vorhandener Schluessel ohne Update-Zweig ruft nichts und antwortet ohne Zeile“ |
| N4 | `Int64` in `Int32` nur im Wertebereich | die Bereichsprüfung entfällt | `SimValuesTest`: „Parameter werden erweitert und im Wertebereich auf Int32 verengt“; `SimulationBackendMergeTest`: „Int64-Parameter in einer Int32-Spalte gilt im Wertebereich“; `SimulationBackendSelectTest`: „ein ungueltiger Wert endet vor dem Lesen“ |
| N5 | der Vergleich nennt genau den Schlüssel der Tabelle | `comparesKey` liefert immer wahr | „nicht erkannte Gestalten enden vor dem Lesen“ |
| N6 | die Zeilenquelle wird genau einmal geschlossen (allein gefahren) | `close()` wird nicht gerufen | „fehlender Schluessel ruft onInsert und antwortet INSERT“, „vorhandener Schluessel ohne Update-Zweig …“ (beide prüfen die Zahl der Schließungen) |
| N7 | meldet der Callback 0, bleibt die Antwort ohne Zeile | die Zeile steht ohne Prüfung des Zählers | „Fehler des Callbacks und negative Zahl sind TDS_BACKEND_ERROR, null ergibt keine Zeile“ |
| N8 | fehlender Callback endet mit `TDS_UNSUPPORTED_SQL` | die Prüfung entfällt | „fehlender Callback endet mit TDS_UNSUPPORTED_SQL“ |
| N9 | `onInsertBatch` ersetzt ein fehlendes `onInsert` | der Ersatz entfällt | „fehlender Schluessel mit Update-Zweig fuegt ein, auch ueber onInsertBatch“ |
| N10 | die Aliase müssen durchgängig sein (allein gefahren) | der Alias vor dem Punkt wird nicht geprüft | „nicht erkannte Gestalten enden vor dem Lesen“ |
| N11 | ein Fehler der Zeilenquelle steht hinter dem Beginn des Ergebnisses | der Fehler gilt als „nicht gefunden“ | „Fehler der Zeilenquelle steht hinter dem Beginn des Ergebnisses“ |
| N12 | `onUpdate` des Bestands lässt keinen doppelten Schlüssel zu | die Eindeutigkeit wird nicht geprüft | `TableStoreTest`: „onUpdate weist ungueltige Aenderungen ab und laesst den Bestand“ |
| N13 | `onUpdate` ändert nur die Zeilen des Filters | der Filter trifft jede Zeile | `TableStoreTest`: „onUpdate aendert die Zeilen des Filters und zaehlt sie“, „ein MERGE fuegt ein, laesst bei skip und aendert bei update“ |
| N14 | `onUpdate` prüft die Werte vor dem Schreiben | die Prüfung entfällt | „onUpdate weist ungueltige Aenderungen ab und laesst den Bestand“ |

Nicht durch eine Mutation gezeigt: die Form der Spalte `$action` als Text und nicht nullbar (der Test vergleicht das Ereignis `ResultStart`, die Mutation wurde nicht gefahren); die Antwort über den Draht in `MergeEmbeddingTest` (rot nur zusammen mit N1 und N3, nicht einzeln).

## Grenzen

- Ein Host, Einzelläufe, jeder Befehl einmal; die Reihenfolge der Läufe baut aufeinander auf. Der Testserver war in einem Stand, der die Änderungen des Slice im Arbeitsbaum trug (nicht der Stand eines Commits).
- Nicht gemessen: ein `MERGE` mit mehr als einer Quellzeile (d-migrate sendet eine), mehrere Verbindungen gleichzeitig auf denselben Schlüssel (Prüfen und Ändern sind nicht atomar, `SPEC-036` Form M), Tabellen mit `DECIMAL`, `TIME`, `DATETIME2` oder `BIT` im `MERGE` von d-migrate (die Überführung der Parameter dafür, `DECIMALN` mit kürzerem Wert und `DATETIMN`, ist **nicht** Teil dieses Slice), große Mengen (der Lauf hat drei Zeilen).
- Die Zahl der Verbindungen und Anweisungen je Lauf ist nicht ausgewertet.
