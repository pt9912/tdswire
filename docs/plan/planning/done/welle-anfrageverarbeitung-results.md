# Welle welle-anfrageverarbeitung — Verarbeitung von SQL_BATCH und RPC nach dem Login (Dispatch, Parameter, Prepared-Statements, mehrteilige Antworten) — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-anfrageverarbeitung
**Abschluss:** 2026-11-07
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

Alle sechs Slices der Welle, die ein Closure-Kriterium tragen, liegen in `done/` (am Dateisystem geprüft, nicht aus den Slice-Notizen übernommen). Die Aussage der Welle steht an Rohbytes-Tests gegen ein Test-Backend, das den Backend-Port implementiert (`ScriptedBackend` und Fixtures), und an dem Einbettungstest `integrator`. Sie gilt für den Server dieses Repos auf dem Pfad „Nachricht einer Sitzung ab `READY` bis zur Antwort“. Sie gilt nicht für einen echten SQL Server; für den Referenzclient `Microsoft.Data.SqlClient` nur dort, wo ein späterer Slice ihn nennt (Abschnitt „Aussagen“).

**Lesart „Rohbytes-Lauf“ (Zusatz dieser Closure, Frage 1 unten).** Der Wortlaut des Mehr in §3 der Welle-Datei sagt „Rohbytes-Lauf“ und §1 „diesen Rohbytes-Client“. Der Gesamttest fährt **Nachrichten** (Pakettyp und Nutzlast) einer Sitzung über `Session` und `FakeHandshakeChannel`, nicht Pakete über einen Socket. Nicht belegt durch ihn: Paket-Framing, Zerlegen der Antwort in Pakete der Packet Size, EOM und Status, Socket, TLS, Login-Bytes. Anderweitig belegt: `StreamingDispatchTest` und `AttentionWireTest` (Paket-Codec, SQL_BATCH-Texte), `RequestWiringTest` (Socket, Batches), `RpcAssemblyTest` und `MessageAssemblerTest` (Zusammensetzen von RPC-Nachrichten), und RPC über den Socket tragen die Läufe außerhalb der Gates (`make kompatibilitaet`, `make e2e`, Referenzclient). Diese Notiz führt die Welle mit dieser Lesart als geschlossen; sie ist eine Lesart des Planners, vom Nutzer nicht bestätigt (Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`).

- `slice-sql-batch-dispatch`: SQL_BATCH in `READY` wird dekodiert und als `QueryRequest` an den Backend-Port gegeben; Ergebnis, leeres Ergebnis und Fehlerfall werden als geordnete Antwort zurückgeführt; der Anfragezustand läuft `READY` → `RUNNING` → `STREAMING` → `READY` (flacher Zuschnitt, `SPEC-001`); die Verbindungsfolge je Fehler kommt aus `ErrorMapper`; [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität).
- `slice-rpc-executesql-parameter`: RPC `sp_executesql` und RPC per Prozedurname (als `EXEC`-Text mit benannten Parametern) mit typisierten Parametern, NULL und Richtung; Werte stehen nie im SQL-Text; unbekannte Formen und PLP werden offen mit `TDS_UNSUPPORTED_RPC` bzw. 50002 abgelehnt, ein Struktur- oder Kodierfehler schließt die Verbindung (Fehlerfolge nach Ursache, Nutzerentscheid 2026-10-31); [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit).
- `slice-antwort-mehrteilig-output`: mehrere Ergebnisse, Update-Zähler, `MORE` und `COUNT`, RETURNSTATUS, RETURNVALUE (OUTPUT und InputOutput, NULL bleibt NULL), DONEPROC; nicht abbildbare Ausgabewerte und Reihenfolgeverstöße ergeben einen definierten Fehler; [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte).
- `slice-rpc-prepared-handles`: `sp_prepexec` und `sp_execute`, Handle-Tabelle je Sitzung, definierte Fehler für unbekanntes Handle und Überschreitung der Grenze (damals 1000, [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md)); [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte).
- `slice-integrations-einstieg`: öffentlicher Einstieg `TdsServer.start(config, source)` mit `BackendSource` und `StartOutcome`, Nachweis nur über die öffentliche Fläche im Source-Set `integrator` (`EmbeddingTest`, `TdsServerLifecycleTest`); [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md).
- `slice-anfragefolge-gesamttest`: das *Mehr* als eigener Slice. `RequestSequenceTest` fährt auf **einer** Sitzung zehn Schritte (Batch mit Ergebnis, leerer Batch, Batch mit zwei Ergebnissen, `sp_executesql` mit NULL und OUTPUT, `sp_prepexec` und `sp_execute`, unbekannter RPC, unbekanntes Handle, Folgeanfragen) und prüft Gleichheit statt Obergrenze (9 Backend-Aufrufe, 11 Antworten, `channel.exhausted`); acht Mutationen M1 bis M8 am Produktivcode wurden rot gesehen.

**Vorrat, kein Closure-Kriterium:** `slice-codec-datentypen-erweiterung` (PLP, NBCROW, Typen außerhalb des Mindestkatalogs; [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)) und `slice-mehrfach-rpc-unterstuetzen` (mehrere RPCs in einer Nachricht; Nutzerentscheid 2026-11-07) bleiben in `open/`. Ihr Gegenstand ist von keinem anderen Slice übernommen und nicht entfallen. Sie verlieren mit dieser Closure die Welle-Bindung (Regel in §3 der Welle-Datei, erster Punkt); ihr Feld `Welle:` ist entsprechend nachgeführt, ihre Start-Trigger sind unverändert.

**Seit den Slice-Closures überholt (Stand `main` zu dieser Closure; die Slices selbst bleiben unverändert).** Die Welle hat Festlegungen gebaut, die spätere Messungen am echten SQL Server oder Entscheidungen verändert haben; der Code und der Gesamttest folgen dem heutigen Stand:

- Antwort-Tokens: Ordinal der RETURNVALUE als Position in der Aufrufliste, RETURNSTATUS in jeder RPC-Antwort, `curCmd` je Form, Fehler im RPC als DONEINPROC bzw. DONEPROC mit Klasse — `slice-antwort-tokens-an-messung-angleichen` ([ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md)); [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) ist `Withdrawn`.
- Handles: RETURNSTATUS 0 vor dem Handle von `sp_prepexec`, Freigabe als Batch `EXEC sp_unprepare n`, Fehler 8179 für ein unbekanntes Handle im Batch, einstellbare Grenze (Voreinstellung 1000) — `slice-handle-freigabe-und-grenze-bauen` ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md)).
- Zuschnitt der Zustandsaufzählung und `OutputParameter` im RPC — [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) (`Accepted`).
- Produktschnitt (Bibliothek als Produkt, Testserver zum Testen) — [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md); `SPEC-059` nachgezogen durch `slice-spec-nachzug-produktschnitt`.
- Der Gesamttest wurde dabei nachgeführt (Commits `bec8ccef`, `dff7a1ef`) und besteht.

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Die Reihenfolge Batch, RPC, Antwortform, Handles, Gesamttest trug: Keiner der Slices musste nach §4 zurückgeführt werden, und jeder fand beim Start seine Voraussetzung in `done/`.
- Das *Mehr* als eigener Slice zu schneiden (anders als in `welle-fehler-abbruch`, die ihren Smoke nie als Slice führte) machte das Closure-Kriterium prüfbar: ein Test mit `@spec`-Tags, Gleichheitszahlen und acht Wächter-Mutationen.
- Die Zuordnung *gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen* in jeder Slice-Closure (`AGENTS.md` §3.8) ließ den späteren Angleich an die Messung am echten Server gezielt an den Festlegungen ansetzen, statt die Welle neu zu lesen.
- Mutationen von Review und Verifikation fanden, was ein grüner Lauf nicht zeigt (z. B. der überlebende Mutant K5 in `slice-sql-batch-dispatch`, die Mutation „Mutex entfernt“ als offener Punkt in `slice-rpc-prepared-handles`).
- Nutzerentscheidungen mitten im Slice (Form des Prozedurnamen-RPC, Fehlerfolge nach Ursache, `@@x`, PLP) wurden vor der Verifikation in den DoD-Wortlaut gezogen; die Regel dazu steht seit `slice-rpc-executesql-parameter` in `implement-slice` (Steering-Loop-Einträge).

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- **Mehrere Festlegungen der Welle wurden von der Messung am echten Server widerlegt oder verändert** (Liste oben). Konsequenz: `slice-antwort-tokens-an-messung-angleichen`, `slice-handle-freigabe-und-grenze-bauen` (beide `done/`); offen bleibt `sp_execute` mit unbekanntem Handle (`SPEC-057`, Fehler 50009, gegen 8179 State 4 am echten Server) — Träger `slice-adr-sp-execute-unbekanntes-handle` (`open/`, Register `BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung`).
- **Vier Spec-Lücken aus `slice-antwort-mehrteilig-output` haben heute je einen Ausgang:** (1) `OutputParameter` im RPC gegen [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 6 — geschlossen durch [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md); (2) Reihenfolge RETURNVALUE gegen RETURNSTATUS — gemessen (Ordinal, RETURNSTATUS in jeder RPC-Antwort); (3) COLMETADATA hinter RETURNVALUE — nicht eingetreten, ohne Träger (Register `BEO-TDS/messung-am-echten-sql-server-fehlt`); (4) DONE statt DONEPROC im Fehlerfall — geschlossen durch [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) und [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md).
- **Der Rahmen von `slice-integrations-einstieg` änderte sich während des Slice** (Produktschnitt, [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)): `main()`, Lader, `simulationFile` und „ein Artefakt“ waren Zwischenstand. Konsequenz: `SPEC-059` ist nachgezogen; `slice-explicit-api-umbau` und `slice-oeffentliche-flaeche-verkleinern-und-belegen` liegen in `done/`.
- **Fünf der sechs Slices haben eine Fix-Runde, die kein zweiter Review gelesen hat** (Verifier und Implementer prüften sie; `slice-sql-batch-dispatch`, `slice-rpc-executesql-parameter`, `slice-antwort-mehrteilig-output`, `slice-rpc-prepared-handles`, `slice-integrations-einstieg`). Das ist je Slice benannt, keine Entscheidung dieser Closure; die Klasse steht im Register (`BEO-TDS/nachbesserung-ohne-zweiten-review`, Lese-Schritt gehört der Closure von `welle-referenzabgleich-release`).
- **Die Lesart des Wellen-Wortlauts „Rohbytes“** blieb über die Slice-Closure hinaus offen (siehe oben, Frage 1).
- **Aussagen breiter als der Beleg** traten in allen sechs Slices wieder auf, obwohl `AGENTS.md` §3.8 galt (Klasse `BEO-TDS/aussagen-ueber-messung-hinaus`, bereits verkörpert; keine weitere Schärfung beschlossen).

## Trigger-Audit der Welle (Schritt 2)

Regeln dieser Sektion: `close-welle` Schritt 2 — je Klasse eine belegte Feststellung.

- **Carveouts:** 0 offen. `docs/plan/carveouts/` führt nur `done/` (CO-001 bis CO-003); kein Slice dieser Welle hat einen Carveout eröffnet oder verlängert.
- **Bootstrap-aware Gates:** 0 offen. Die Sensors-Tabelle in `harness/README.md` führt kein Gate mit Stufe oder Reife; die Zeilen mit `bootstrap-aware` sind Vorlagenreste.
- **Entscheidungen (Re-Evaluierungs-Trigger der ADRs der Welle):**

| ADR | Trigger | Befund |
|---|---|---|
| [ADR-0004](../../adr/0004-session-zustandsautomat.md) | neue Nachrichtentypen, Schreibwettlauf, Messung widerspricht der Setzung bei unzulässigen Übergängen | nicht eingetreten; der Zuschnitt der Zustandsaufzählung ist durch [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) geschlossen |
| [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) | neuer Aufruftyp erzeugt unbekannte RPC-Form; echter Server beantwortet Katalogformen anders | **eingetreten, bearbeitet:** Antwortformen durch [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md); der Rest (`sp_execute` mit unbekanntem Handle) ist als Entwurf-Slice `slice-adr-sp-execute-unbekanntes-handle` in `open/` geführt; `sp_unprepare` als RPC sendet kein gemessener Client, [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) Offen 5 hält die Re-Evaluierung fest |
| [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../../adr/0007-fehler-und-attention.md) | Backend und Fehler, Träger sind die Wellen `welle-backend-streaming` und `welle-fehler-abbruch` | in deren Closure-Notizen geprüft, hier nicht erneut |
| [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) | Messung des Handle-Verhaltens; Betreiber will die Zahl ändern | eingetreten, durch [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) teilweise ersetzt (Zählung, einstellbare Grenze); die Überschreitung (Entscheidung 2) gilt weiter |
| [ADR-0013](../../adr/0013-backend-grenzen.md) | OutputParameter im RPC | durch [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) teilweise ersetzt |
| [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) | — | `Withdrawn`, Nachfolge der Frage [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) |

Keine Entscheidung steht mit eingetretener, unbearbeiteter Bedingung da; die eine offene Folge-Entscheidung hat einen Träger-Slice.

## Aussagen der Welle: gemessen und Festlegung

Regeln dieser Sektion: Closure-Kriterium §3 der Welle-Datei, vierter Punkt. *Gemessen* heißt: ein Test, der besteht (Stage `test` oder Source-Set `integrator`, Frischlauf dieser Closure, siehe Verifikation), gegen das Test-Backend und den Sitzungsrahmen. *Festlegung* heißt: MS-TDS-Lesart, Setzung des Nutzers oder Entscheidung eines Slice ohne Messung am Referenzclient oder echten Server. Wo eine spätere Messung am echten Server eine Festlegung verändert hat, steht das in der dritten Spalte; ein Handvektor zählt als Festlegung (von Hand gezählt oder aus einem Mitschnitt abgespielt, Quelle im Test). Die Angaben zu Mutationen in dieser Tabelle stammen aus den Closure-Notizen der Slices; diese Closure hat nur die im Abschnitt Verifikation genannte nachgefahren.

| Aussage | gemessen (Test, der besteht) | Festlegung / Stand heute |
|---|---|---|
| SQL_BATCH gelangt unverändert an den Backend-Port | `SqlBatchDispatchTest` (fünf Texte, Parameterliste leer, Frist `null`); Mutanten M8, M12 rot | Verbindung bleibt nach einem Anfragefehler offen (Class 16, Setzung des Nutzers); der Text des Backends erreicht den Client nicht |
| Anfragezustand läuft `READY` → `RUNNING` → `STREAMING` → `READY`; Matrix 126 Zellen | `SqlBatchDispatchTest`, `HandshakeMatrixTest` (32 zulässig, 6 ignoriert, 88 unzulässig); Gesamttest: Rückkehr indirekt (Annahme der Folgenachricht) | Lesart: `READY` ist der Anfragezustand `IDLE` (`SPEC-001`), `COMPLETED` ist Übergang; ATTENTION in jedem Anfragezustand ist Zielbild, nicht Teil dieser Welle |
| Fehler vor jeder Zeile ergibt `TDS_UNSUPPORTED_SQL`, Folgebatch gelingt; sechs schließende Ausgänge | `DispatchFailureTest`, `DispatchClosingTest`, `RequestWiringTest` (Socket); M1, M11, M14, M15 rot | Tokenfolge und Statusbits der Fehlerantwort am echten Server: Mitschnitt-Lauf der späteren Welle (lokal, nicht in den Gates) |
| `sp_executesql`: Text unverändert, Werte nur als Parameter, NULL und Richtung; RPC per Name als `EXEC`-Text | `RpcBinderTest`, `RpcDispatchTest`; R3, R7, R9, R10, M5 bis M8 rot | Form `EXEC`-Text, `@@x` nur auf dem EXEC-Pfad abgelehnt, Grammatik der Namen und Zählregel 128, Wertebereichs-Ablehnung (Nutzerentscheide 2026-10-31 und Setzungen des Implementers); Wertlayout und `EXEC`-Text am Referenzclient: gemessen in `slice-qa-kompatibilitaet` (`done/`), am echten Server nur soweit der Mitschnitt-Lauf sie abspielt |
| unbekannte oder gültig kodierte nicht unterstützte Form: ERROR und DONE, Verbindung bleibt; PLP offen abgelehnt (50002); Struktur- und Kodierfehler schließen | `RpcDispatchTest`, `RpcFailureTest`; Mutationen M1, M2 der Nachverifikation rot (Tests von `0f97567` vom Implementer allein, kein dritter Kontext hat sie gefahren) | Trennlinie „schließen gegen offen“ und Abschluss der Antwort: Setzung, seit [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) entschieden (Option B); am echten Server ist die Antwort auf eine ungültig kodierte RPC-Nachricht ungemessen (akzeptiertes Negativ) |
| mehrere Ergebnisse, `MORE`/`COUNT`, Update-Zähler in Stromreihenfolge | `AnswerFormsDispatchTest`; M4, M6 rot | `curCmd` je Form und Ordinal sind seit `slice-antwort-tokens-an-messung-angleichen` nach der Messung am echten Server geführt (Mitschnitt-Lauf lokal); Lücken bleiben: Flag-Feld des Ausgabewerts (A-20), `curCmd` nach Fristablauf und von `EXEC sp_unprepare`, Register `BEO-TDS/zielbild-luecken-der-antwort-tokens` |
| RETURNSTATUS, RETURNVALUE, DONEPROC für OUTPUT; Reihenfolgeverstöße und nicht abbildbare Werte ergeben einen Fehler | `AnswerFormsDispatchTest`, `ResponseRpcTest`; M1 bis M8 der Verifikation rot | RETURNSTATUS in jeder RPC-Antwort und Ordinal als Position: gemessen am echten Server (Messprotokoll), im Mitschnitt-Lauf abgespielt, nicht in den Gates; Byte-Vektoren sind Handvektoren, nicht am Referenzclient |
| `sp_prepexec` liefert ein Handle, `sp_execute` ruft das Backend mit gemerktem Text; Handle-Tabelle je Sitzung; Grenze | `PreparedHandlesTest`, `PreparedHandlesDispatchTest`, `HandleReturningBackendTest`; Mutationen M1 bis M3 rot | Fehlercodes 50009 und 50010 (`SPEC-057`, `SPEC-058`), „gesendet heißt gültig“, Zähler ab 1; **Stand heute:** RETURNSTATUS vor dem Handle und einstellbare Grenze ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), gebaut); `SPEC-057` gegen 8179 State 4 am echten Server offen (`slice-adr-sp-execute-unbekanntes-handle`); die Lesart „eigener Mutex statt Sitzungsschutz“ ([ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) Entscheidung 3) ist vom Nutzer unbestätigt |
| öffentlicher Einstieg von außerhalb des Hauptmoduls, Validierung vor dem Start, `Aborted`, `stop` idempotent | `EmbeddingTest`, `TdsServerLifecycleTest`, `integratorOhneAssoziation`; Mutationen m1 bis m3 rot | Form des Einstiegs, `Aborted(reason, issues)`, Passwort-Löschung beim Laden: Setzungen ([ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), `SPEC-059`); nicht gemessen: `stop` aus mehreren Threads, Lauf ohne Jackson beim Einbettenden, Wiederbinden des Ports |
| **Das Mehr:** eine Sitzung, zehn Schritte; Zustand kehrt nach jeder Anfrageart zurück, Handle überlebt Batch, RPC und zwei Fehler, ein Fehler beendet die Sitzung nicht | `RequestSequenceTest` (Frischlauf PASSED); Gleichheit 9 Aufrufe, 11 Antworten, 9 Schließaufrufe; Wächter siehe Verifikation | Tokenfolge der Antworten und das Ende der Folge sind Festlegungen (Handvektoren der Vorläufer-Tests); Fehlercodes 50002 und 50009; Rückkehr nach `READY` nur indirekt; „Rohbytes“ meint Nachrichten (Lesart oben, Frage 1) |

## Ausgeschlossene Gegenstände (§6 der Welle-Datei) und ihr Träger

Regeln dieser Sektion: Closure-Kriterium §3 der Welle-Datei, vierter Punkt — jeder ausgeschlossene Gegenstand ist einem Träger zugeordnet, der als Datei existiert, oder trägt den Grund, warum nicht.

| Gegenstand (§6) | Träger (Datei) | Stand zu dieser Closure |
|---|---|---|
| Backend-Port, `Flow<QueryEvent>`, Typkatalog, Resultset-Kodierung, Sequenzierer-Kern | `welle-backend-streaming-results.md` (`done/`) | geschlossen |
| Fehlerabbildung, ATTENTION, Cancel, Timeout, Protokoll-Synchronität | `welle-fehler-abbruch-results.md` (`done/`) | geschlossen |
| Handshake, Login, `READY`, Bootstrap | `welle-handshake-login-results.md` (`done/`) | geschlossen |
| Sockets, TLS, Listener, Beenden | `welle-transport-tls-results.md` (`done/`), für das Beenden `welle-fehler-abbruch-results.md` | geschlossen |
| Simulations-Backend (ARC-016) | `welle-simulations-backend-results.md` (`done/`); jedes echte Backend: kein Träger im Repo, Grund: ein echtes Backend ist Sache des Einbettenden ([TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend)) | geschlossen |
| RPC-Formen außerhalb des Katalogs (`sp_prepare`, `sp_unprepare`, `sp_cursor*`, TVP, MARS); Transaktionen (`0x0E`); `sp_reset_connection` | `sp_unprepare` im Batch: `slice-handle-freigabe-und-grenze-bauen` (`done/`); `sp_reset_connection`: `slice-resetconnection-zuruecksetzen` (`done/`); Transaktionen und `SET`-Texte am Referenzclient: `slice-qa-kompatibilitaet` (`done/`); mehrere RPCs in einer Nachricht: `slice-mehrfach-rpc-unterstuetzen` (`open/`); Cursor, TVP, MARS: kein Träger, Grund: kein gemessener Client sendet sie, ein Slice entsteht erst nach Nachweis des Bedarfs ([ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)) | `SET`-Text bei `SchemaOnly`/`KeyInfo` und `FMTONLY`: Register `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger`, **offen, Entscheid beim Nutzer** (Träger-Vorschlag dort) |
| Typen außerhalb des Mindestkatalogs im Backend, Länge von `UnicodeText` | `slice-codec-datentypen-erweiterung` (`open/`, Vorrat) | Setzung des Nutzers 2026-10-31 unverändert: bis dahin ein definierter Fehler |
| Nachweis gegen Referenzclient und echten SQL Server | `slice-qa-kompatibilitaet`, `slice-qa-reproduzierbarkeit`, `slice-qa-vergleichslauf-d`, `slice-qa-vergleichslauf-d-messungen`, `slice-referenzabgleich-live`, `slice-referenzabgleich-mitschnitt` (alle `done/`); weitere Messpunkte: `slice-qa-vergleichslauf-d-messungen-bulk-merge` (`open/`) | TDS 8.0, Pooling: Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (offen, Frage 3) |
| Last und Durchsatz | `slice-qa-performance` (`open/`) | offen |
| Fuzz über ungültige Anfragen in einer angemeldeten Sitzung | **kein Träger.** Grund: `slice-qa-stabilitaet` (`done/`) fuzzte nur Anmeldepfad und Parser; die Beobachtung `BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger` steht auf *offen* ohne Slice; ein Slice dafür ist nicht angelegt | Frage 4 |
| Observability-Adapter (ARC-009, `SPEC-017`) | `welle-betrieb-diagnose.md` (flach in `planning/`) | `slice-betrieb-log-schnittstelle` und `slice-betrieb-log-adapter-testserver` in `done/`; `slice-betrieb-metriken-tracing` mit dieser Closure startbar |
| Neue Anforderungen oder Änderungen am Lastenheft | — | keine entstanden |

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks; die
**Spec-Lücke** trägt statt `liegt in` ihre `LH-*`-ID — das ist kein Versehen).

**Lese-Schritt des Beobachtungs-Registers.** Gelesen wurden alle Verzeichnisse von `BEO-TDS` mit mindestens drei Evidence-Dateien, in denen eine Datei einer der sechs Slices dieser Welle liegt (Stand 2026-11-07, am Dateisystem gezählt). **Mit dieser Closure wird nichts neu verkörpert.** Der Grund: Jede Klasse über der Schwelle hat bereits einen Ausgang, oder ihr Ausgang ist an eine Entscheidung des Nutzers gebunden, die im Register steht (Nutzerentscheid 2026-11-06 für `lesart-…`) oder noch fehlt (Fragen 2 und 3). Die Anker-Paarung entfällt für neue Einträge; für die zwei Einträge, die in den Slices dieser Welle verkörpert wurden, ist sie unten geprüft.

Verkörpert in den Slices dieser Welle (wellenlos nach `grundlagen-traceability.md`, Regel `seit slice-…`):

- **Ablauf** geschärft: Weicht eine Nutzerentscheidung vom DoD-Wortlaut ab, ändert der Planner den Wortlaut vor der Verifikation — liegt in `.claude/commands/implement-slice.md`. Auslöser: `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt` (`slice-adr-backend-grenzen`, `slice-adr-simulation-formen`, `slice-rpc-executesql-parameter` — 3× an der Closure von `slice-rpc-executesql-parameter`; Anker dort `seit slice-rpc-executesql-parameter`, am Ziel gefunden).
- **Ablauf** geschärft: Hat ein Slice Tests geändert, läuft `make spec-abdeckung` vor dem Closure-Commit und die Matrix liegt im selben Commit — liegt in `.claude/commands/implement-slice.md`. Auslöser: `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (`slice-antwort-mehrteilig-output`, `slice-qa-spec-abdeckung`, `slice-rpc-executesql-parameter` — 3× an derselben Closure; Anker `seit slice-rpc-executesql-parameter`, am Ziel gefunden).

Klassen, deren Belege in dieser Welle liegen, gruppiert nach Ausgang:

1. **Schon verkörpert, kein Ausgang nötig** (Anker im Register-`state.md`): `aussagen-ueber-messung-hinaus` (`AGENTS.md` §3.8 und Reviewer-Skill, `seit welle-protokollcodec`; alle sechs Slices), `zeile-oder-zweig-ohne-unterscheidenden-test` (Reviewer-Skill, `seit welle-transport-tls`; `slice-sql-batch-dispatch`, `slice-rpc-prepared-handles`), `abdeckungsmatrix-veraltet-ohne-sensor` und `dod-satz-verlangt-ablage-die-der-slice-ersetzt` (siehe oben), `risikozeile-nennt-behobenen-zustand` (`plan-welle`, `seit welle-backend-streaming`; `slice-rpc-executesql-parameter`, `slice-rpc-prepared-handles`), `aussage-ueber-schichtgrenze-ohne-test` (Reviewer-Skill, `seit slice-e2e-backend-ausserhalb-modul`; `slice-rpc-executesql-parameter`), `fortschreibung-laesst-zweite-fundstelle-stehen` (Reviewer-Skill, `seit slice-qa-dotnet-lieferkette-haerten`; `slice-antwort-mehrteilig-output`), `plan-tabelle-nennt-neue-datei-nicht` (Reviewer-Skill, `seit slice-e2e-backend-ausserhalb-modul`; `slice-antwort-mehrteilig-output`), `test-stage-cache-verdeckt-wiederholung` (`make clean-build`, `seit slice-handshake-login`; `slice-anfragefolge-gesamttest`), `offene-punkte-ohne-traeger` (`implement-slice`, `seit welle-entscheidungen-grundlage`; `slice-sql-batch-dispatch`); `kennungen-ohne-link-in-neuen-dokumenten` ist gestrichen (`make docs-check` deckt die Klasse).
2. **Geplant, mit Träger:** `messung-am-echten-sql-server-fehlt` (Messreihe am Draht W-1 bis W-9 und Mitschnitt, Träger der Reste in `slice-qa-vergleichslauf-d-messungen-bulk-merge`, `slice-katalog-indizes-defaults-fremdschluessel` und `slice-adr-sp-execute-unbekanntes-handle`; Belege dieser Welle: `slice-sql-batch-dispatch`, `slice-rpc-executesql-parameter`, `slice-antwort-mehrteilig-output`, `slice-rpc-prepared-handles`).
3. **Über der Schwelle und offen — gelesen, nicht verkörpert, Entscheidung beim Nutzer:**

| Klasse (`BEO-TDS/…`) | Evidence | Beitrag dieser Welle | Ausgang dieser Closure |
|---|---|---|---|
| `lesart-einer-nutzerentscheidung-unbestaetigt` | 16 | `slice-rpc-executesql-parameter`, `slice-rpc-prepared-handles`, `slice-anfragefolge-gesamttest` | Nutzerentscheid 2026-11-06 gilt: bei der Welle-Closure gelesen, ohne Regel, Sensor oder Träger-Slice; hier gelesen, unverändert. Die Lesarten dieser Welle stehen als Fragen 1 und 5 |
| `tds-8-0-und-pooling-nicht-gemessen` | 20 | `slice-rpc-executesql-parameter`, `slice-rpc-prepared-handles` | gelesen, unverändert *offen*; Empfehlung der Closure von `welle-fehler-abbruch` (Rest in `slice-qa-vergleichslauf-d-messungen-bulk-merge` aufnehmen) ist nicht entschieden, Frage 3 |
| `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` | 7 | `slice-integrations-einstieg` | gelesen, unverändert *offen*; die Entscheidung ist laut `state.md` der Closure von `welle-testserver-manuell` zugewiesen, Frage 2 |

**Kein Eintrag über der Schwelle wurde in dieser Closure neu zur Regel.** Das ist das Ergebnis des Lese-Schritts, nicht sein Auslassen.

Neue Beobachtungen dieser Welle (je unter der Schwelle, vom Sichtungs-Schritt von `/plan-welle` zu lesen): `BEO-TDS/test-naht-im-produktivcode` (`slice-sql-batch-dispatch`, 2×), `BEO-TDS/meldungstabelle-von-hand-neben-der-folge` (`slice-anfragefolge-gesamttest`, 1×), `BEO-TDS/lebenszyklus-im-fremden-prozess-nicht-gemessen`, `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen`, `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze`, `BEO-TDS/move-commit-laesst-detekt-rot` (alle `slice-integrations-einstieg`), `BEO-TDS/test-tempdir-aufraeumen-race-unter-last` (`slice-rpc-prepared-handles`, 1×).

**Benannte Spec-Lücken** (in der Spezifikation oder im Register als offen geführt, kein `liegt in`): (1) `SPEC-057` gegen 8179 State 4 am echten Server (Träger `slice-adr-sp-execute-unbekanntes-handle`, Messvermerk an der Zeile); (2) Zielbild-Lücken der Antwort-Tokens (A-20, `curCmd`) mit Re-Evaluierungs-Trigger nach der A-20-Messung (Register `BEO-TDS/zielbild-luecken-der-antwort-tokens`); (3) Mehrfach-RPC am echten Server ungemessen (Träger `slice-mehrfach-rpc-unterstuetzen`); (4) PLP und NBCROW in der Spezifikation als Lücke mit Träger `slice-codec-datentypen-erweiterung`; (5) `SchemaOnly`, `KeyInfo`, `FMTONLY` ohne Träger (Register, oben).

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat und gelesen wurde, steht oben unter
*Steering-Loop-Einträge*.

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren; genannt ohne angelegt ist dieselbe Klasse wie
ein halluziniertes Gate.

- `slice-codec-datentypen-erweiterung` (`open/`) — Vorrat, ohne Welle-Bindung; Start-Trigger: Beleg, dass der Referenzclient die Formen sendet.
- `slice-mehrfach-rpc-unterstuetzen` (`open/`) — Vorrat, ohne Welle-Bindung; Start-Trigger: Priorisierung durch den Nutzer.
- `slice-adr-sp-execute-unbekanntes-handle` (`open/`) — Entscheidung zu `SPEC-057` gegen 8179 State 4.
- `slice-qa-vergleichslauf-d-messungen-bulk-merge` (`open/`) — trägt die A-20-Messung und weitere Messpunkte.
- `slice-qa-performance` (`open/`) — Last und Durchsatz.
- `slice-betrieb-metriken-tracing` (`open/`) — der Wellen-Teil seines Start-Triggers ist mit dieser Closure erfüllt; offen ist ein Verantwortlicher im Kopf.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl). Einzelmessungen am 2026-11-07, ein Host, Basis `main` mit `32fcc71b`.

- Closure-Trigger am Repo geprüft: Die sechs Slices der Welle liegen in `done/` (`ls`); `slice-codec-datentypen-erweiterung` und `slice-mehrfach-rpc-unterstuetzen` liegen in `open/` (Vorrat, kein Kriterium). `RequestSequenceTest` existiert (`src/test/kotlin/…/protocol/dispatch/RequestSequenceTest.kt`, eine Testfunktion, `@spec` SPEC-001, SPEC-002, SPEC-030, SPEC-057, SPEC-013).
- Frischlauf der Stage `test` ohne Layer-Cache (`docker build --no-cache-filter test --target test .`): Exit 0, `BUILD SUCCESSFUL in 2m 42s`, 1088 `PASSED`-Zeilen, keine rote Zeile eines Testfalls (sieben Treffer auf `FAILED` sind Testnamen mit PASSED), 2 übersprungen, `integratorTest` grün; `RequestSequenceTest > Die Gesamtfolge der Anfragearten laeuft auf einer Sitzung ohne Zustandsverlust()` PASSED.
- Wächter des Gesamttests am heutigen Stand gesehen: Mutation in `RequestLoop.kt` (Abschluss einer Anfrage setzt `connectionOpen = false`, die Sitzung endet nach der ersten Antwort), Stage `test` mit Filter `*RequestSequenceTest`, ohne Cache: Test rot mit „Antworten: nach 1 folgt Antwort 1 (Schritt 2, Batch leer) ==> expected: <11> but was: <1>“; Mutation zurückgenommen, Arbeitsbaum danach ohne Änderung an `src/`. Die acht Mutationen M1 bis M8 mit ihrem ersten roten Schritt stehen in der Closure-Notiz von `slice-anfragefolge-gesamttest` (verifiziert und gefahren am 2026-11-01); diese Closure fuhr nur die eine Mutation nach, die übrigen sind nicht neu gefahren.
- Nicht in dieser Closure gefahren: `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d` (lokal, keine Gates, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); sie tragen den Socket- und Referenzclient-Pfad, der den Gesamttest nicht trägt.

**Gate-Läufe dieser Closure:** `make test` (mit Cache) Exit 0 vor dem Self-Close-Commit. `make docs-check` nach dem Self-Close-Commit, nach dem Move und nach der Link-Reconciliation: Exit 0, 1109 Dateien, 0 Befunde (zwischen Move und Reconciliation war er rot, `target-missing`, wie vorgesehen). `make gates` nach der Reconciliation: Exit 0. Die Klassen `werkzeug`, `tests` und `stempel` wurden dabei **durch ihren Stempel übersprungen** (kein Eingang der Klassen hat sich in den Commits dieser Closure geändert; `make gates FORCE=1` erzwingt den Lauf), die Klasse `immer` lief voll; der Frischbeleg der Tests ist der Lauf der Stage `test` oben, nicht der Stempel.

## Offene Fragen an den Nutzer

Diese Closure trifft keine davon. Jede trägt einen Träger und eine Empfehlung.

1. **Lesart „Rohbytes-Lauf“ = Nachrichten, nicht Transport.** Der Slice `slice-anfragefolge-gesamttest` hielt fest, die Welle schließe nicht ohne diesen Zusatz. Diese Closure führt die Welle mit der Lesart „Nachrichten einer Sitzung“ als geschlossen, auf Auftrag des Aufrufers und ohne Bestätigung des Nutzers. Träger: Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`. *Empfehlung:* Lesart bestätigen; ein Gesamtlauf über `WireSession` oder Socket brächte gegenüber `RequestWiringTest` und den Referenzclient-Läufen keine neuen Fälle. Verneint der Nutzer, ist die Welle zu öffnen und der Slice „Gesamtfolge über `WireSession`“ (RPC-Überlader im Test-Support) anzulegen.
2. **`kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (7×).** Ein Reviewer-Punkt „Kommentar trägt keine der Kommentar-Klassen“ steht im Reviewer-Skill (Baseline-Text, ohne Herkunfts-Anker); `AGENTS.md` §3.7 trägt die Regel. Offen: ein eigener Anker, ein Sensor oder nichts. *Empfehlung (wie in der Closure von `welle-fehler-abbruch`):* kein Sensor, die Klasse ist Urteil; die Entscheidung bei der Closure von `welle-testserver-manuell` belassen.
3. **Messlücke TDS 8.0 und Pooling (20×, *offen*).** Kein Träger für den Rest (mehrpaketiges LOGIN7, TLS 1.2 und andere Suiten, `sp_unprepare` als RPC, Paketierung der Antworten). *Empfehlung:* in `slice-qa-vergleichslauf-d-messungen-bulk-merge` aufnehmen, soweit die Messreihe sie nicht schon misst.
4. **Fuzz über ungültige Anfragen in angemeldeter Sitzung** (`BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger`, ohne Träger). *Empfehlung:* einen Slice anlegen, sobald `slice-qa-performance` oder die nächste QA-Welle geplant wird; vorher kein Vorrats-Slice.
5. **Lesart „eigener Mutex der Handle-Tabelle erfüllt ‚unter dem Sitzungsschutz‘“** ([ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) Entscheidung 3, Planner-Lesart aus `slice-rpc-prepared-handles`). *Empfehlung:* bestätigen; eine Klarstellung per Folge-ADR nur, wenn der Nutzer die Lesart nicht teilt.
6. **WIP-Limit „pro Rolleninhaber“** (Review F-6, Verifikation F-V4 in `slice-anfragefolge-gesamttest`): zählt das Limit je Lauf oder je Git-Identität? Alle Läufe tragen `pt9912`. *Empfehlung:* das Feld `Verantwortlich:` unterscheidet die Läufe nicht; das Limit als „je Worktree“ lesen oder das Feld um den Lauf ergänzen.
