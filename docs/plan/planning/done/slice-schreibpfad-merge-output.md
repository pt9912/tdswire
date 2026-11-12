# Slice slice-schreibpfad-merge-output: MERGE ... OUTPUT $action: Form in der Bibliothek, Konfliktbehandlung beim Integrator, data import und data transfer mit --on-conflict skip und update

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** SPEC-036 (neue Form für `MERGE` mit VALUES-Zeile, Alias und `OUTPUT $action`; bisher „OUTPUT-Klauseln nicht erkannt“) · SPEC-030 (Parametertypen) · SPEC-035 (Callbacks) · SPEC-040

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-07.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Die Bibliothek erkennt das `MERGE` mit Parametern, gibt es nach der Entscheidung an den Integrator weiter und liefert dessen Ergebnis als Ergebniszeile `$action`; der Testserver führt die Konfliktbehandlung gegen seinen Bestand aus, sodass `data import` und `data transfer` von d-migrate mit `--on-conflict skip` und `update` durchlaufen und ein Export die Zeilen enthält (Hürde 10 der Sonde).

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Gemessen ist der Text und die Parametertypen (Int64, UnicodeText, Date, vom Binder angenommen); die Sonde beantwortete das MERGE mit einer festen Zeile, nichts wurde gespeichert. Der Abbruch-Pfad (`abort`) nimmt laut Sonde den Massenpfad; ein `INSERT` mit Parametern wurde nicht beobachtet. **Messung liegt vor:** die Antwort des echten SQL Servers auf `MERGE … OUTPUT $action` mit Parametern ist gemessen (Messung M-13 bis M-15, [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07); Referenzclient, Klauseln hinter `USING (VALUES …)` und Spaltenliste sind Setzung des Messcodes, Instanzen 2022 und 2025); die Setzungen dieses Slice und von `SPEC-036` zu Ergebnismenge, Änderungszähler und Fehlern sind daran zu messen. **Hülle:** Der Plan ist knapp; der Implementer erweitert §3 im ersten Lauf, nachdem die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) vorliegt (Form der Weitergabe und Namen der Fläche hängen an ihr). **Aufteilung:** Die Funktion liegt in der Bibliothek (`src/main`, Packages `simulation`, `protocol`); der Testserver (`testserver`) ist Integrator: Bestand, Callbacks, Beispieltabellen. Jeder Liefer-Punkt unten nennt seinen Anteil.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Standardpfad mit Paket 0x07 — [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md).
- Allgemeines `MERGE` (mehrere Quellen, mehrere WHEN-Zweige, `DELETE`-Zweig, beliebige `OUTPUT`-Spalten): erkannt wird nur die Gestalt, die d-migrate sendet; alles andere bleibt `TDS_UNSUPPORTED_SQL`.
- Katalog und DDL — [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md).
- Konfliktsemantik jenseits von Schlüsselvergleich (Bedingungen, Trigger); die Bibliothek speichert und vergleicht nichts selbst ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [x] **Bibliothek, Form und Weitergabe:** SPEC-036 legt die Form des MERGE fest, das d-migrate sendet (Erkennung am Text, Zuordnung der Parameter); die Weitergabe an den Integrator, die Ergebnismenge `$action` und der Änderungszähler sind festgelegt (am echten SQL Server gemessen, Referenzclient, Instanzen 2022 und 2025, Einzelmessungen: Ergebnismenge `$action` nvarchar(10) nicht nullbar, Zähler im `DONEINPROC` Befehl `0x0117`, Fehlerfälle 2628, 2627 und 208, M-13 bis M-15 im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07), Zeiger statt eigener Messung; **Abweichung vom Plan-Wortlaut „Setzung“**: die Setzungen sind an diesen Zeilen zu messen; die Klauseln hinter `USING (VALUES …)` und die Spaltenliste sind Setzung des Messcodes, nicht der Wortlaut von d-migrate). **Messauftrag (Nutzerentscheid 2026-11-07, Folgepflicht 2 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)):** vor dem Bau der Form werden der Wortlaut des `MERGE` von d-migrate und die INT-Spalten im Standardpfad mit dem JDBC-Client (mssql-jdbc 13.4.0, wie d-migrate) am echten SQL Server (Digest-Pin, EULA: Nutzer) nachgemessen; Beleg: Zeilen im Messprotokoll mit der nächsten freien Nummer, getrennt von den Zeilen des Referenzclients. Die Zählung der Liefer-Punkte bleibt bei drei (der Messauftrag gehört zu Punkt 1); wächst der Messauftrag über einen Lauf hinaus, ist die Zerlegung benannt: Messung | Bibliothek | Testserver und Beleg; Tests je Zweig und für NULL-Parameter und Typgrenzen ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Boundary).
- [x] **Testserver:** Der Testserver führt Einfügen, wenn der Schlüssel fehlt, bei `skip` unverändert, bei `update` ändern, gegen seinen Bestand aus und meldet die Aktion zurück.
- [x] **Beleg am fremden Client:** `data import` und `data transfer` mit `--on-conflict skip` und `--on-conflict update` gegen den Testserver enden mit Exit 0; ein `data export` danach zeigt die erwarteten Zeilen; Ausgabe mit Rechner, Last und Image-Digest.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
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
| `tools/jdbc-messung/Aufnahme.java`, `tools/vergleichslauf-d.sh` (Lauf `jdbc-*`), `tools/mk/vergleichslauf-d.mk` | neu, update | Liefer-Punkt 1, Messung: d-migrate 1.12.0 (mssql-jdbc 13.4.0) gegen die Referenzinstanz über einen mitschneidenden Durchlauf; Erzeuger im Repo |
| `referenztest-dotnet/Mitschnitt/messung/jdbc-d-migrate/`, `docs/messungen/referenzabgleich.md` (M-25 bis M-32) | neu, update | Liefer-Punkt 1: Ablage der Auswertung beider Instanzen, Zeilen getrennt von denen des Referenzclients |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (`MergeParts`, `SimulationMerges`, `Statement`, `StatementParser`, `StatementPlanner`, `SimulationBackend`, `SimulationChanges`, `SimValues`) | neu, update | Liefer-Punkt 1, Bibliothek: Form M erkennen, Werte überführen (Narrowing `Int64` in `Int32`), Zerlegung in Lesen und einen Callback, Antwort `$action`; kein neuer öffentlicher Name |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/TableStore.kt` | update | Liefer-Punkt 2, Testserver: `onUpdate` im Bestand (Schlüssel als Filter, Prüfung wie beim Einfügen); das Einfügen läuft über `onInsertBatch` |
| `spec/spezifikation.md` (`SPEC-036` Form M, `SPEC-035`, `SPEC-040`, Änderungsprotokoll) | update | Form M, Setzungen gekennzeichnet; offene Punkte (Länge von `$action`, Befehl `0x0117`) benannt |
| Tests: `SimulationBackendMergeTest`, `SimValuesTest`, `MergeEmbeddingTest` (Einbettung über den Draht), `TableStoreTest` | neu, update | Happy/Boundary/Negative nach [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), NULL-Parameter und Typgrenzen ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/RpcParameterReader.kt`, `protocol/dispatch/RpcParameters.kt`, `protocol/dispatch/RpcWireValues.kt` | update | Review F-1, `SPEC-030`: `DECIMALN` mit kurzem Wert (2 bis 17 Byte), `DATETIMN` mit 8 Byte als Zeitpunkt, wie mssql-jdbc sie sendet (M-31) |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/RowRules.kt` | neu | Prüfregeln des Bestands, aus `TableStore` herausgelöst (detekt: Funktionszahl) |
| `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/RpcWire.kt`, `MergeEmbeddingTest.kt` | update, neu | Draht-Helfer für `bigint`, Text, Datum, rohe Parameter, `sp_prepexec` mit Werten und `sp_execute`; Einbettungstests |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendSelectTest.kt`, `SimValuesTest.kt` | update | ein Testwert (`Int64` über dem Bereich von `Int32`) und die neuen Verengungen |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcBinderTest.kt`, `protocol/rpc/RpcTypesTest.kt`, `protocol/rpc/RpcFailureTest.kt`, `src/integrator/kotlin/io/github/pt9912/tdswire/integrator/RpcParams.kt` | update, neu | Tests der neuen Parametertypen (gemessene und nach MS-TDS gesetzte Vektoren getrennt) und der abgelehnten Längen; Hilfsfunktionen für Parameter in der Einbettung |
| `referenztest-dotnet/Kompatibilitaet/AnmeldungTests.cs`, `docs/plan/kompatibilitaetsmatrix.md`, `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md` (erzeugt) | update | `DATETIMN` mit 8 Byte wird gelesen und beantwortet, mit 4 Byte abgelehnt: Referenztest, Matrixzeilen und Wortlaut der Stufe nachgezogen |
| `docs/user/einbettung-und-betrieb.md`, `docs/plan/planning/done/slice-antwort-tokens-zielbild-luecken-bauen.md` | update | Nutzer-Doku zu `MERGE` (Sperre, Nicht-Reentranz, Callback, `primaryKey`); Träger der offenen Messpunkte |
| `harness/README.md` (Zelle `make vergleichslauf-d`), `docs/plan/spec-abdeckung.md` (erzeugt) | update | Gate-Index und Abdeckungsmatrix |
| `docs/messungen/merge-output-2026-11-07.md`, `docs/user/testserver-manuell.md` | neu, update | Liefer-Punkt 3: Läufe von d-migrate gegen den Testserver mit Rechner, Last und Image-Digest; knappe Nutzer-Doku |

**Abweichung vom Plan-Wortlaut („Plan vor Code“).** §3 ist nicht vor der ersten Zeile Code ausformuliert worden: der Plan stand im Kopf des Implementers, die Messung (Commit `8fc2fea6`) ging dem Bau voraus, und die Tabelle samt Ist-Zustand steht erst im Commit `23242f68`, danach ergänzt aus dem Review. Dass der Ist-Zustand vor dem Bau gemessen wurde, belegt die Historie nicht; belegt ist der Ist-Zustand durch den Vergleich des Diffs mit der Wurzel `f5250140`.

**Zerlegung und Reihenfolge.** Messung, Bibliothek, Testserver und Beleg sind drei Schritte in dieser Reihenfolge; die Messung geht dem Bau voraus, weil der Wortlaut des `MERGE` und die Antworten aus ihr kommen (Spezifikation ist Zielbild, der echte Server die Referenz). Bleibt ein Schritt über einen Lauf hinaus offen, wird dort geschnitten und der Rest in einen Folge-Slice gegeben (Rückführung `in-progress` → `next`, §4). **Ist-Zustand vor dem Bau gemessen:** `Statement` kennt kein `MERGE`, der Scanner liest `$action` als Wort, die Tabellen des Bestands haben `onInsertBatch` und kein `onUpdate`, `SimValues` verengt einen `Int64`-Parameter nicht. **Öffentliche Fläche:** kein neuer Name; zwei Abweichungen vom Messbefund (Länge von `$action`, Befehl `0x0117`) brauchten ein neues Feld an einem öffentlichen Typ (`ResultColumn`, `QueryEvent.ResultEnd`) und sind deshalb nicht gebaut ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4).

**Anschluss an die Entscheidung** ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted` am 2026-11-06, Messungen: [Messprotokoll des Spikes](../../../messungen/callback-loesung-spike-2026-11-05.md); Liste in §3 von [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)). Spec-Änderungen dieses Slice: `SPEC-036` (Form `MERGE … OUTPUT $action` für `skip` und `update`, „`OUTPUT`-Klauseln“ verlassen die Liste „Nicht erkannt“; der vollständige Wortlaut ist aus einem erneuten Mitschnitt zu übernehmen, der Spike hat ihn nur teilweise im Log), `SPEC-035` (Zerlegung in Lesen über die Zeilenquelle und höchstens einen Callback; der Vorfilter bleibt ein Hinweis, die Bibliothek prüft den Schlüssel; Verengung von Int64 auf Int32 im Wertebereich), `SPEC-030` (`DECIMALN` mit kürzerem Wert als der Typ, `DATETIMN` mit 8 Byte als Zeitpunkt, Zeit als `DATETIMN`), `SPEC-040` (`--on-conflict skip|update`). Entschieden (ADR `Accepted`, 2026-11-06): `MERGE` und die Lesart „abschließend“ sind bestätigt; die Handle-Grenze ersetzt eine eigene neue ADR (Folge-Slice vor diesem Slice, ersetzt Entscheidung 1 und 4 von [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md)), nicht dieser Slice. Start-Trigger unverändert (§4), zusätzlich: die Folge-ADR zur Handle-Grenze ist `Accepted` ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), `Accepted` am 2026-11-06; Antwortform, Freigabe und Eingang der Grenze baut [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) vor diesem Slice). Messung zur Grenze: [handle-grenze-optionen-2026-11-06](../../../messungen/handle-grenze-optionen-2026-11-06.md) — mssql-jdbc sendet ohne RETURNSTATUS in der `sp_prepexec`-Antwort ein `sp_prepexec` je Ausführung und nie `sp_unprepare`; mit ihm sendet es `sp_unprepare` als SQL-Batch. Spec-Liste in [slice-adr-handle-grenze-ersatz](../done/slice-adr-handle-grenze-ersatz.md) §3; der Bau-Slice [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) ist ein weiterer Start-Trigger dieses Slice. Aus dem Spike: ohne die drei Änderungen am RPC-Parameter lehnt die Bibliothek den MERGE von d-migrate ab („Unsupported RPC.“ bzw. „Protocol error.“, a7); die Handle-Grenze 1000 aus [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) bricht den Import nach rund 1000 Zeilen je Sitzung (a6, Entscheid der ADR vom 2026-11-06); `MERGE` nennt das Lastenheft nicht wörtlich (Entscheid der ADR vom 2026-11-06).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) liegt in `done/` (Weitergabe, Katalog nach Änderungen und Fläche entschieden) und [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) liegt in `done/` (Bestand des Testservers besteht) und [slice-adr-handle-grenze-ersatz](../done/slice-adr-handle-grenze-ersatz.md) liegt in `done/` (die ADR, die Entscheidung 1 und 4 von [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) ersetzt; ohne sie bricht der Import nach 1001 `MERGE` je Sitzung ab, Messzeile a6 des Spikes) und [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) liegt in `done/` (baut Antwortform, Freigabe und einstellbare Grenze aus [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md); ohne ihn bricht der Import nach 1001 `MERGE` je Sitzung ab); damit stehen [slice-testserver-gradle-modul](../done/slice-testserver-gradle-modul.md) und [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) vor diesem Slice (Kette über den Katalog-Slice). Reihenfolge vom Nutzer am 2026-11-06 entschieden ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Anteil der Bibliothek und Anteil des Testservers als zwei Slices.
- `in-progress` → `open` (blockiert): die Entscheidung in [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md) trägt den Fall nicht oder wird überstimmt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

DoD vollständig, `make gates` grün, die Ausgabe der d-migrate-Läufe liegt vor, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Der Messauftrag mit dem JDBC-Client braucht eigenen Messcode (der Referenzclient ist nicht mssql-jdbc) und die EULA-Entscheidung des Nutzers für das gepinnte Image (2026-11-06 bestätigt); entfällt der Lauf, bleibt die Form des `MERGE` Setzung nach der Sonde. — **Ausgang:** entfallen: die Messung M-25 bis M-32 liegt vor (beide Instanzen) und ist vom Verifier mit `jdbc-2022` und `jdbc-2025` wiederholt (Parameter-Bytes und Exit-Codes gleich der Ablage).
- Aufwand der Sonde: M bis L; die Erkennung von `MERGE` weitet die Liste der Formen, die SPEC-036 „abschließend“ nennt (nicht vom Nutzer bestätigt). — **Ausgang:** weiter offen: Setzung ohne Bestätigung des Nutzers, Träger `SPEC-036` (Form M); die Frage geht im Bericht der Closure an den Nutzer.
- Die Gestalt des MERGE hängt an d-migrate 1.12.0 und seiner Spaltenliste; eine andere Version kann einen anderen Text senden (nicht gemessen). — **Ausgang:** weiter offen als benannte Grenze (`SPEC-036`, `tools/vergleichslauf-d.sh`); nicht eingetreten.
- Da die Bibliothek nichts speichert, muss die Weitergabe die Konfliktsemantik (Schlüsselvergleich, Aktion) tragen; ob dafür ein eigener Callback nötig ist oder eine Folge aus Lesen und Einfügen genügt, entscheidet [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md). — **Ausgang:** entfallen: ein Callback (Lesen über die Zeilenquelle, höchstens ein Schreib-Callback), gebaut und getestet.
- Ob d-migrate beim `MERGE` seine Anweisungsobjekte schließt oder wiederverwendet, ist nicht gemessen; davon hängt ab, ob die Freigabe aus [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) die Grenze für diesen Pfad entschärft (nur der Treiber ist gemessen). — **Ausgang:** entfallen mit Beleg: in der Ablage beider Instanzen (`2022.aufnahme.txt`, `2025.aufnahme.txt`) kommt kein `sp_unprepare` vor (je 13 `sp_prepexec`, 18 `sp_execute`, 470 `sp_executesql`); d-migrate gibt im Lauf kein Handle frei, die Grenze aus [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) bleibt für diesen Pfad die wirksame. Grenze: ein Lauf mit drei bis fünf Zeilen je Schritt.
- Länge der Spalte `$action` (`nvarchar(10)`, neues Feld an `ResultColumn`), Befehl `0x0117` des Abschlusses und die Fehlernummern des `MERGE` (2628, 2627, 208, M-15; die Bibliothek sendet `TDS_BACKEND_ERROR`) sind Abweichungen vom echten Server ohne Bau. Träger: [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md) (Risiko dort eingetragen); das Feld an einem öffentlichen Typ braucht vorab den Entscheid des Nutzers ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4). — **Ausgang:** eingetreten: [slice-merge-antwort-an-messung-angleichen](../done/slice-merge-antwort-an-messung-angleichen.md) (Lieferpunkte a bis c; Entscheid des Nutzers zu `ResultColumn` vorab), Register [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md).
- Der Client wählt den Typ seiner Parameter selbst: d-migrate 1.12.0 sendet Ganzzahlwerte als `bigint`, die strenge Überführung ([SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)) weitet nur `Int32` auf `Int64`; ein Filter `Id = 7` gegen eine `Int32`-Spalte endete im Export mit „Invalid value“ (Beobachtung [parametertyp-des-clients-passt-nicht-zur-spalte](../observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/observation.md), Ursache aus dem Code gelesen, nicht gemessen). Der Slice prüft die Parameter seines Pfades gegen die Spaltentypen. — **Ausgang:** eingetreten und behoben: `Int64` in `Int32` im Wertebereich, `DECIMALN` mit kurzem Wert, `DATETIMN` mit 8 Byte; belegt (M-29, M-31, M-32, S6 bis S10), Register [`BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte`](../observations/BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte/state.md).
- Stufe 7 von `make e2e` (`SynchronitaetTests.Cancel_und_Command_Timeout_kurz_hintereinander_…`) wird unter hoher Last gelegentlich rot (Zweig 2 von 15, `b2ff295c` 0 von 15 bei Last 41 bis 94; kein Pfad mit `MERGE` im Test); Träger: `BEO-TDS/wanduhr-tests-flackern-unter-last` (Ursache nicht geklärt; ein Einfluss dieses Slice ist nicht ausgeschlossen, aber nicht belegt). — **Ausgang:** weiter offen: → `BEO-TDS/wanduhr-tests-flackern-unter-last` ([Register](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/state.md)); auf `6d07620b` 9 von 9 grün, keine Zuordnung behauptet.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Belege: Review [F-1 bis F-14](../../../reviews/2026-11-07-b2ff295c-review-slice-schreibpfad-merge-output.md), Re-Review [R-1 bis R-10](../../../reviews/2026-11-07-f7f9ff33-re-review-slice-schreibpfad-merge-output.md), Verifikation [V-1 bis V-9](../../../reviews/2026-11-08-6d07620b-verifikation-slice-schreibpfad-merge-output.md) (0 HIGH, 1 MEDIUM, 4 LOW, 4 INFO; Gates, `make clean-build` mit 1230 bestandenen Tests, `make e2e`, `make matrix-zitate`, `make flaeche`, `make vergleichslauf-d` mit `jdbc-2022` und `jdbc-2025`, d-migrate S1 bis S13, sieben Mutationen in eigenen Läufen). Der Zweig ist mit `main` (Stand `d18a8541`) zusammengeführt; ein Textkonflikt in `slice-antwort-tokens-zielbild-luecken-bauen.md` ist gelöst (Inhalt von `main`).

- **Was hat funktioniert:** Die Messung mit dem JDBC-Client ging dem Bau voraus und trug ihn: der Wortlaut des `MERGE` von d-migrate, die `bigint`-Parameter und die Verengung nach `Int32`, die Zerlegung in Lesen und einen Callback und `DECIMALN`/`DATETIMN` kamen aus M-25 bis M-32, nicht aus der Sonde. d-migrate läuft gegen den Testserver mit `--on-conflict skip` und `update` (S1 bis S13; drei gleichzeitige Importe: jeder Schlüssel genau einmal). Die Mutationen des Verifiers röteten die Tests, die das Messprotokoll nennt.
- **Was ging anders als geplant:** Zwei Review-Runden fanden mehr als einen Plan-Fehler (Plan-Tabelle zweimal unvollständig, Parser gebaut und die Fundstellen des Typs nicht nachgezogen, „gemessene“ Vektoren außerhalb der Aufnahme). Drei Abweichungen vom Messbefund blieben ungebaut (Länge von `$action`, Befehl `0x0117`, Fehlernummern) und wechselten den Träger, weil `main` den ersten umschrieb; sie haben jetzt einen eigenen Slice. Der Test der Stufe 7 flackerte unter Last (2 von etwa 24 Läufen), ohne Zuordnung.
- **Zuordnung der Zeilen M-25 bis M-32** (was ist Messung, Handvektor, Festlegung):

  | Zeile | Messung (echter Server 2022 und 2025, mssql-jdbc 13.4.0) | Handvektor (nach MS-TDS-Lesart, nicht am echten Server aufgenommen) | Festlegung (Setzung) |
  |---|---|---|---|
  | M-25 | Wortlaut des `MERGE` von d-migrate | — | andere Aliase als `tgt` und `src` gelten, wenn durchgängig; die Liste der erkannten Formen ist „abschließend“ |
  | M-26 | Tokenfolge, `$action` `nvarchar(10)` nicht nullbar, Befehl `0x0117`, leeres Ergebnis ohne ROW | — | Länge 4000 und Befehl `0x00c1` der Bibliothek (Abweichung, ungebaut) |
  | M-27 | Ergebnis von `data import` und `data transfer` mit `skip` und `update` | — | — |
  | M-28 | `INT` im Standardpfad scheitert am echten Server (Grenze von d-migrate und mssql-jdbc); Folgepflicht 2 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) ist damit erfüllt | — | — |
  | M-29 | `MERGE` in eine `INT`-Spalte mit `bigint`-Parameter, Exit 0 | — | Verengung `Int64` nach `Int32` im Wertebereich |
  | M-30 | `FEATUREEXTACK`-Merkmale der Anmeldung | — | — |
  | M-31 | Parameter-Bytes `DECIMALN` (2 bis 17 Byte Wert, `01 00` für 0,00) und `DATETIMN` (`…00 00 00 00`, `…FF 81 8B 01`, NULL) | `datetime`-Grenzwerte 1900-01-01, letzter Tick am Tag 0 und 0001-01-01 (eigener Test „nach der Lesart von MS-TDS“) | Abweisung von `DATETIMN` mit 4 Byte; Rundung der Ticks auf Millisekunden |
  | M-32 | Ergebnis mit `DECIMAL`, `TIME`, `DATETIME2`, `BIT`, `DATE` (Exit 0, Export gleich) | — | Sperre je Backend, Nicht-Reentranz, hängender Callback; `time`-Bruchteile über `datetime` verengt |

- **Benannte Spec-Lücken** (kein `liegt in`; Träger die Spezifikation oder ein Slice): (1) Bruchteile einer Sekunde in einer `time`-Spalte: gemessen ist nur, dass der Export von d-migrate `23:59:59` für `23:59:59.997` zeigt (beide Instanzen, vom Verifier wiederholt), nicht, was die Spalte speichert (`time(7)`); Träger `SPEC-036` (Form M, „Nicht gemessen“), V-7. (2) Abbruch am Draht im Warten auf die Sperre: weder gemessen noch getestet (R-7, Rest). (3) Kein Test-Vektor für `0,00` als `DECIMALN` mit Länge 2 (`01 00`): gemessen in der Ablage beider Instanzen und im Lauf `transfer-decimal-zeit`; der Leser ist generisch (Vorzeichen 1, Betrag 0); Grenze statt Test, V-8, ein Test ist Sache eines Implementers. (4) `MERGE` gegen eine Tabelle ohne Schlüssel endet mit `TDS_UNSUPPORTED_SQL`, am echten Server ist es möglich (V-9); Träger [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md) (§6).
- **Steering-Loop-Eintrag:** Lerneinträge, **gezählt, nicht verkörpert** (kein `liegt in`; die Zählung steht in den Evidence-Dateien dieses Slice, der Lese-Schritt gehört der Closure der Welle): (a) *Eine Messung mit dem echten Client macht eine Setzung zur Messung* (Wortlaut des `MERGE`, Parametertypen): Setzungen der Sonde vor dem Bau am echten Client messen, `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`; (b) *der Auswerter ist bei einem neuen Typ nicht vollständig* (Parser gebaut; Spezifikation, E2E-Stufe, Nutzer-Doku und Vektoren nicht nachgezogen), `BEO-TDS/auswerter-ohne-neuen-typ-nachgezogen`, neu; (c) *die Plan-Tabelle ist zum vierten Mal unvollständig* (F-5, R-4), obwohl die Regel im Reviewer-Skill verkörpert ist: sie greift beim Reviewer, nicht beim Implementer; ein Sensor Tabelle gegen Diff bleibt Entscheid des Nutzers, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`; (d) *Nachbesserung mit Code ohne zweiten Review* (Vermerk unten), `BEO-TDS/nachbesserung-ohne-zweiten-review`; (e) *eine Zielbild-Abweichung ohne Lieferpunkt* wechselte den Träger, weil `main` ihn umschrieb: ein Risiko „offen bis zur Closure“ in einem fremden Slice ist kein Träger, ein kleiner Slice mit Lieferpunkten ist einer, `BEO-TDS/abweichungstraeger-stirbt-beim-merge-von-main`, neu.
- **Vermerk „Nachbesserung ohne zweiten Review“** (`BEO-TDS/nachbesserung-ohne-zweiten-review`): die Commits `963a0474` (Nachbesserung zum Re-Review, R-1 bis R-5 und R-7 bis R-10), `6aa9410e` (Merge von `main`), `0f5031ba` (Messreihe zu R-6) und `6d07620b` haben keinen zweiten Review in frischem Kontext. Stattdessen: der Verifier hat `f7f9ff33..6d07620b` wie ein Reviewer bewertet, die Behebungen R-1 bis R-10 nachvollzogen und sieben Mutationen selbst gefahren (0 HIGH). Entscheidung des Planners: kein dritter Review; der Merge von `main` danach (`28408366`) ändert nur Dokumente, weder `src` noch `testserver`.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-schreibpfad-merge-output.md` ergänzt in `BEO-TDS/zielbild-luecken-der-antwort-tokens`, `BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`, `BEO-TDS/wanduhr-tests-flackern-unter-last` (4×, Ausgang beim Nutzer, keine Zuordnung zu diesem Slice behauptet), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, `BEO-TDS/nachbesserung-ohne-zweiten-review`, `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server` und `BEO-TDS/parametertyp-des-clients-passt-nicht-zur-spalte`; neu angelegt `BEO-TDS/auswerter-ohne-neuen-typ-nachgezogen` und `BEO-TDS/abweichungstraeger-stirbt-beim-merge-von-main` (je 1×); `BEO-TDS/messung-am-echten-sql-server-fehlt` fortgeschrieben (M-25 bis M-32, Folgepflicht 2 von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) erfüllt).
- **Folge-Slices:** [slice-merge-antwort-an-messung-angleichen](../done/slice-merge-antwort-an-messung-angleichen.md) (Träger der drei Abweichungen aus V-1); [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md) (nimmt den Fall `MERGE` ohne Schlüssel in §6 auf, V-9); [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md) (legt `StatementKind` an, Vorgänger des Folge-Slice); [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md) (Abnahme mit d-migrate).
- **Risiken aus §6:** jedes mit genau einem Ausgang, siehe §6 — entfallen: Messauftrag, Konfliktsemantik, Anweisungsobjekte; eingetreten: Abweichungen der Antwort (Träger [slice-merge-antwort-an-messung-angleichen](../done/slice-merge-antwort-an-messung-angleichen.md)), Parametertyp (behoben); weiter offen: Setzung der Liste „abschließend“, Gestalt an d-migrate 1.12.0, Stufe 7 (Register).
- **Drei Paarungen:** Anker — kein Lerneintrag trägt `liegt in`, nichts zu paaren; Folge-Slice — die vier genannten Dateien existieren; Register — jede genannte Beobachtungskennung ist ein Verzeichnis mit nicht leerem `evidence/`. Ergebnis im letzten Haken von §2 nach dem Move.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen); kein Treffer ausgemacht. Der Implementer sichtet das Register bei Start erneut.

Alle berührten Sub-Areas GF.
