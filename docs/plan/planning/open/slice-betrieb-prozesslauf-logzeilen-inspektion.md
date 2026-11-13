# Slice slice-betrieb-prozesslauf-logzeilen-inspektion: Ein Rohbytes-Lauf über den Testserver-Prozess und eine Inspektion über alle erfassten Zeilen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Ereignismodell und Format der Zeile), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Kindprozess als E2E-Lauf, Entscheidung 4: Prozesstests der Stage `test`)

**Berührte Spec-Stellen:** SPEC-017 · SPEC-018 · SPEC-019 (Pflicht-Attribute der Spans) · SPEC-052 (Zeile des Log-Adapters, Eingänge des Testservers) · SPEC-043 (nur der erfolgreiche Start; der abgebrochene Start bleibt im bestehenden Test) · SPEC-048 (Typname statt Ausnahmetext im Fehlerereignis) · SPEC-012 (abgelehnte Anmeldung ohne Kennung) · SPEC-053 (nur als Grenze: Drosselung ab dem 6. Fehlversuch einer Adresse, der Lauf bleibt darunter) · spezifikation.md §4 (Pflicht-Logfelder, Herkunft der IDs, Redaktion durch Bauart) · spezifikation.md §5 (Spans, Metriken)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-13 (Entscheid: Weg B, „Code angleichen“; der Closure-Trigger der Welle bleibt im Wortlaut). **Datum:** 2026-11-13.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Das „Mehr“ des Closure-Triggers von [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) §3 ist **ein** Lauf belegt: ein Rohbytes-Client spricht den Testserver-Prozess (Start, Login angenommen und abgelehnt, SQL_BATCH, parametrisierte Anfrage, Fehler, Cancel, Beenden), und ein Inspektionstest über alle JSON-Zeilen dieses einen Laufs findet weder Passwort noch Prüfwert noch Parameterwert noch SQL-Text, findet an jeder Zeile die Pflichtfelder und findet die drei Spans mit ihren Pflicht-Attributen. Heute belegen das nur getrennte Tests ([TestserverLogProcessTest](../../../../src/test/kotlin/io/github/pt9912/tdswire/runtime/TestserverLogProcessTest.kt): Anmeldung angenommen und abgelehnt, SQL_BATCH, Spans bei `DEBUG`, Metriken, SIGTERM; Cancel mit Spans nur im Prozess des Tests, `MetricsTracingWiringTest`); eine Parameterlast und ein Fehlerpfad der Zeilen fehlen am Prozess.

**Stand der Abklärung (Planner, aus dem Bestand gelesen, nicht gelaufen):**

- **(a) Namen.** Die Pflicht-Attribute der Spans stehen in `spezifikation.md` §5 (`SPEC-017`: `connection.id`, `tds.phase`, `outcome`; `SPEC-018`: `session.id`, `request.id`, `tds.request_kind`, `outcome`; `SPEC-019`: `request.id`, `duration_ms`, `outcome`). In der Zeile heißen sie `connection_id`, `phase`, `outcome`, `session_id`, `request_id`, `request_kind`, `duration_ms`; der Name des Spans steht im Feld `span` (`tds.connection`, `tds.query`, `backend.execute`), die Art im Feld `event` (`span_ended`). Belege: `spec/spezifikation.md` Zeilen 1757 bis 1759 (Tabelle), 1786 bis 1789 (Abbildung der Namen auf die Felder des Ereignisses), `testserver/kotlin/io/github/pt9912/tdswire/testserver/TelemetryJson.kt` Zeilen 51 bis 72 (Feldname je Zugriff; die Schreibweise mit Unterstrich ist Setzung des Adapters, `SPEC-052`). Die Pflicht-Logfelder der Zeile (`ts`, `level`, `event`, `connection_id`, `session_id` falls vorhanden, `request_id` falls vorhanden, `duration_ms`, `error_code`, `client_profile`, `principal` nur bei angenommener Anmeldung) stehen in `spezifikation.md` §4 Zeile 1647 ff.; welche Art welches Feld trägt, leitet der Test aus §4 und §5 ab (Tabelle im Test, Festlegung, nicht gemessen).
- **(b) Backend des Prozesses.** Das `main` des Testservers (`Main.kt` Zeile 37, `storeSource` Zeile 44) legt ein `SimulationBackend` über den `TableStore`; seine Tabellen sind endlich (Datei der Beschreibung oder `CREATE TABLE`), es gibt dort **keine** Anfrage ohne Ende. Das `main` des Kompatibilitätsservers (`KompatibilitaetsServer.kt` Zeile 163, `ServerProcess.RUNNING_TABLE_MAIN`, von `ServerSmokeTest` schon als Kind-JVM gestartet) bindet dieselbe Bibliothek und denselben `LogAdapter` (`runTestserver`), hat die Tabelle `laufend` (`LaufendeQuelle.kt`: Zeilen ohne Ende, jede nach 5 ms Pause, endet nur durch den Abbruch; ein Cancel braucht damit weder Wanduhr noch Last) und die Tabellen `person` (Auswahl, auch mit Bedingung auf einen Parameter: das Backend ist ein `SimulationBackend`, `RecordingBackend` schreibt die Parameter als Name, Typ und Richtung, nie als Wert) und `kaputt` (die Quelle endet nach drei Zeilen mit einer Ausnahme, deren Text `Geheimnis der Quelle` nirgends erscheinen darf). **Ohne Produktcode lösbar: ja**, mit dem Kompatibilitätsserver als Prozess. Preis, der als Frage an den Nutzer steht (§6, Frage 1): `RecordingBackend` schreibt je Anfrage eine **Nicht-JSON-Zeile** `kompatibilitaet: anfrage … sql=[…]` mit dem **SQL-Text** auf die Standardausgabe. „Alle erfassten Zeilen“ sind darum die **JSON-Zeilen des Adapters** (so sagt es auch §3 der Welle: „Logzeilen (JSON-Zeilen des Adapters)“); die Zeilen des `RecordingBackend` sind ausgenommen und werden einzeln geprüft (Präfix, kein Passwort, kein Parameterwert).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Kein Referenzclient und keine Stufe von `make e2e`: Die Aussage der Welle steht am Testserver-Prozess mit Rohbytes-Client ([welle-betrieb-diagnose](../welle-betrieb-diagnose.md) §1); ein Referenzclient gehört nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) in den E2E-Durchgang und wäre kein Test der Stage `test`.
- Keine Änderung des Adapters, des Testservers oder der Bibliothek (kein Produktcode): Weg B gleicht den Code an die Spezifikation an, **soweit** er abweicht; dieser Slice belegt den Ist-Stand. Zeigt die Vorprüfung (§3) eine Abweichung des Produkts, ist sie ein eigener Slice (Rückführung in §4), nicht Teil dieses.
- Keine neue öffentliche Fläche: kein Typname, kein Zugriff; `make flaeche` bleibt bei der Liste von `SPEC-059`.
- Kein Ersatz der vorhandenen Einzeltests: `TestserverLogProcessTest` (abgebrochener Start, `LOG_LEVEL`, nie gelesener Kanal), `MetricsTracingWiringTest` und `TelemetryRedactionTest` (Redaktion durch Bauart) tragen Teilaussagen, die der eine Lauf nicht wiederholt; er ergänzt sie um die Zusammenschau.
- Kein abgebrochener Start und kein belegter Port im Lauf: „Start“ heißt hier der erfolgreiche Start (`server_started`); die Diagnose eines abgebrochenen Starts belegt der bestehende Test.
- Keine Messung des Overheads der Telemetrie und keine Last: [slice-qa-performance](slice-qa-performance.md) trägt sie; dieser Slice sagt nichts „unter Last“, seine Lastunabhängigkeit folgt aus der Bauart des Tests (§6).
- Kein TLS im Lauf (`TEST_UNENCRYPTED`, wie die bestehenden Prozesstests): die Zeilen des Handshakes mit TLS sind nicht Gegenstand des „Mehr“.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei; ein Test, eine Schicht: `src/test`). Jeder Punkt trägt einen **Mutationsnachweis**: die Mutation (ein vorübergehender Eingriff in `src/main` oder `testserver/`, nicht eingecheckt) macht den Test rot, und zwar an der Behauptung, die der Punkt nennt, nicht an einem Folgefehler; die Mutation und die Fehlermeldung stehen in der Closure-Notiz.

- [ ] **Ein Lauf, vollständig erfasst ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)):** Ein neuer Test (Kind-JVM über `ServerProcess.RUNNING_TABLE_MAIN`, `LOG_LEVEL=DEBUG`, `METRICS_INTERVAL_SECONDS=1`, Test-Umgebung wie `TestserverLogProcessTest`) führt in **einem** Prozess nacheinander aus: Login angenommen, Login abgelehnt (ein Fehlversuch, unter der Schwelle der Drosselung `SPEC-053`), SQL_BATCH mit Ergebnis, eine parametrisierte Anfrage (RPC `sp_executesql`, ein Parameterwert, Ergebnis als Antwort belegt), eine Anfrage mit Fehler der Quelle (Tabelle `kaputt`), eine Anfrage ohne Ende (`laufend`) mit ATTENTION (Cancel, Antwort endet mit `DONE[ATTN]`, Verbindung bleibt offen), dann Schließen der Clients und SIGTERM. Jeder Schritt wartet auf ein Ereignis (Bytes der Antwort, eine Zeile der Ausgabe), keiner auf eine Zeit; die gesammelte Ausgabe steht in jeder Fehlermeldung. *Mutation:* der Server beantwortet ATTENTION nicht (oder beendet `laufend` nicht) — der Test ist rot an der fehlenden `DONE[ATTN]`-Antwort bzw. an `tds.query` mit `outcome` ungleich `cancelled`.
- [ ] **Inspektion über alle JSON-Zeilen ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)):** Der Test prüft über alle erfassten JSON-Zeilen des Laufs (a) jede Zeile ist gültiges JSON mit `ts`, `level`, `event`; die Pflichtfelder je Art (`connection_id`, `session_id` ab angenommener Anmeldung, `request_id` in den Enden von `tds.query` und `backend.execute` und im Ereignis `request_failed`, `duration_ms` in `span_ended`, `error_code` in `request_failed`, `client_profile`, `principal` nur in `login_accepted`) stehen dort, wo `spezifikation.md` §4 und §5 sie verlangen (Tabelle im Test); (b) jedes Feld jeder Zeile gehört zur Menge der Felder der Spezifikation (eine unbekannte Kennung wird rot, nicht übersehen, `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert`); (c) weder die beiden Passwörter noch der Prüfwert (`pbkdf2`) noch die Kennung der abgelehnten Anmeldung noch der Parameterwert noch ein Stück des SQL-Textes (Auswahl-, Bedingungs- und Tabellenname der gestellten Anfragen) noch der Text der Ausnahme der Quelle (`Geheimnis der Quelle`) steht in einer JSON-Zeile; die Zeilen `kompatibilitaet: …` des `RecordingBackend` sind ausgenommen und einzeln geprüft (Präfix, weder Passwort noch Parameterwert). *Mutation:* `cause` im Fehlerereignis trägt den Text der Ausnahme statt des Typnamens (Rot an `Geheimnis der Quelle`), und `duration_ms` fehlt in der Zeile `span_ended` (Rot an dem Pflichtfeld).
- [ ] **Spans und Metriken ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), Eingang für [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance)):** Der Test findet die Enden der drei Spans mit ihren Pflicht-Attributen in den Namen der Zeile (siehe §1 (a)): `tds.connection` (`connection_id`, `phase`, `outcome`; zweimal: `login_rejected` in `handshake`, `ok` in `session`), `tds.query` (`session_id`, `request_id`, `request_kind` `sql_batch`/`rpc`, `outcome`: `ok`, `ok`, `error`, `cancelled`), `backend.execute` (`request_id` gleich der des `tds.query` derselben Anfrage, `duration_ms`, `outcome`), und danach ein `metrics` mit der erwarteten Zahl Anfragen, einer Anmeldung, einer abgelehnten Anmeldung, einem Abbruch und `spans_open` 0; `request_failed` des Fehlerschritts trägt dieselbe `request_id` wie das Ende von `tds.query` dieser Anfrage (Grund des Start-Triggers, §4). Die erwarteten Werte stammen aus `spezifikation.md` §5 und sind nicht gemessen; der Implementer gleicht sie am ersten Lauf ab und nennt jede Abweichung in der Closure-Notiz. *Mutation:* das Ende von `backend.execute` trägt eine andere `request_id` (Rot an der Gleichheit), `outcome` von `tds.query` bei Cancel ist `ok` (Rot an `cancelled`).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Der Test trägt im KDoc über der Testfunktion die Block-Tags `@spec SPEC-017`, `@spec SPEC-018`, `@spec SPEC-019`, `@spec SPEC-052` (nur, was er wirklich prüft); `make spec-abdeckung` einmal, `docs/plan/spec-abdeckung.md` mit dem Ergebnis eingecheckt (Konflikt: Fassung von `main`, neu erzeugen nur wegen der Tags dieses Slice).
- [ ] Der Test läuft in der Stage `test` (`make test`) und ist grün; wie der bestehende `TestserverLogProcessTest` steht er nicht in der Stage `smoke` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidung 4 hält die Prozesstests in der Stage `test`; die Stage `smoke` nennt im `Dockerfile` nur `ServerSmokeTest` und `ZustandsereignisseProzessTest`).
- [ ] Jede Wartestelle des Tests ist in der Closure-Notiz mit ihrem Signal aufgeführt (Bytes, Zeile, Latch); die Schranke einer Wartestelle (Zeitgrenze, bei der der Test mit der Ausgabe abbricht) ist keine Erwartung der Dauer. Die Laufzeit des Tests aus dem Gradle-Bericht ist einmal genannt (ohne Aussage „unter Last“).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: keiner öffentlichen Schnittstelle berührt; die Closure-Notiz sagt das, und `docs/user/` ist nicht Gegenstand (kein Produktcode).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/TestserverLaufInspektionTest.kt` (Name Vorschlag) | neu | der eine Lauf und die Inspektion (Happy-Pfad aller Schritte, Negativ: Fehler der Quelle, abgelehnte Anmeldung, Cancel); `@spec SPEC-017`, `SPEC-018`, `SPEC-019`, `SPEC-052`; neue Datei statt Anbau an `TestserverLogProcessTest` (360 Zeilen, sechs Tests mit eigener Hilfsfunktionen-Gruppe): der eine Lauf mit Inspektion ist eine eigene Aussage; die Grenzen von detekt prüft `make detekt` (Schnitt ist Vorschlag des Planners, der Implementer darf anbauen, wenn `make detekt` grün bleibt) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/ServerProcess.kt`, `RuntimeSupport.kt` | update, nur falls nötig | eine Hilfe zum Warten auf eine Zeile der Ausgabe nach Bedingung statt nach Präfix; keine Änderung des Bestehenden |
| `docs/plan/spec-abdeckung.md` | update | Ergebnis von `make spec-abdeckung` (Tags) |

**Vorprüfung vor dem Bau (Implementer, erster Schritt, kein Produktcode):** die vier Annahmen des Plans am Bestand belegen und in der Closure-Notiz festhalten, bevor der Test geschrieben wird: (1) das Backend des Kompatibilitätsservers nimmt `sp_executesql` mit einer Bedingung auf einen Parameter an und antwortet mit einer Ergebnismenge (`RpcWire.executeSql` und `RpcWire.nvarchar` in `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcWire.kt` liefern die Bytes); (2) der Fehler der Quelle von `kaputt` (nach drei Zeilen) erzeugt am Prozess ein Ereignis `request_failed` mit `error_code` und `cause` als Typname und nicht mit dem Text der Ausnahme; geht stattdessen die Verbindung zu, wählt der Lauf den Fehler einer unbekannten Tabelle (Antwort mit Fehlercode, das Backend lehnt den Text ab, vgl. `TestserverLogProcessTest` Zeile 260), und der Fehler der Quelle bleibt als Befund; (3) ATTENTION während `laufend` wird am Prozess mit `DONE[ATTN]` beantwortet und `tds.query` und `backend.execute` enden mit `cancelled` (im Prozess des Tests belegt in `MetricsTracingWiringTest`, am Serverprozess bisher nur SIGTERM in `ServerSmokeTest`); (4) `LOG_LEVEL=DEBUG` zeigt `span_ended` am Kompatibilitätsserver wie am Standard-`main` (beide über `runTestserver` und `BackendSource.Custom`, `BEO-TDS/backend-ueber-tablestore-meldet-kein-bereit-ereignis` betrifft nur `simulation_backend_ready`). Widerlegt eine Annahme das Produkt (nicht den Test), gilt die Rückführung in §4.

**Bauart gegen die Wanduhr (Vorgabe an den Test):** Cancel erst, wenn Bytes der Antwort von `laufend` da sind (`readSome` nicht leer); danach Lesen bis `DONE[ATTN]`. Die Clients schließen erst, wenn das Ende von `tds.query` als Zeile steht; SIGTERM erst, wenn für jede Verbindung das Ende von `tds.connection` und ein `metrics` mit `spans_open` 0 und der erwarteten Zahl der Anfragen als Zeilen stehen (Zeile als Signal). Zeit kommt nur als Schranke vor (Abbruch mit Ausgabe), nie als Erwartung; `METRICS_INTERVAL_SECONDS=1` ist der Takt der Bibliothek, auf den der Test per Zeile wartet, keine Frist, die er abwartet.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-request-failed-mit-request-id](../in-progress/slice-request-failed-mit-request-id.md) liegt in `done/`, und ein Verantwortlicher ist im Kopf eingetragen. Grund der Kante: Das Ereignis `request_failed` des Fehlerschritts trägt erst mit diesem Slice die `request_id` seiner Anfrage; die Inspektion (§2 Punkt 3) prüft, dass sie dieselbe ist wie im Ende von `tds.query`, und die Pflichtfelder je Art (§2 Punkt 2) nennen `request_id` dort. Ohne den Slice müsste der Test das Feld an dieser Art ausnehmen und bei seiner Lieferung nachziehen (zweimal anfassen). Beobachtbar: die Datei liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder der Test braucht eine zweite Schicht außer `src/test`.
- `in-progress` → `open` (Entscheid nötig): (1) die Vorprüfung in §3 widerlegt eine Annahme **am Produkt** (zum Beispiel: `request_failed` fehlt beim Fehler der Quelle, ATTENTION wird am Prozess nicht beantwortet, ein Feld fehlt in einer Zeile) — dann liefert den Angleich ein eigener Slice im Sinn von Weg B, dieser Slice wartet; (2) der Nutzer verlangt, dass „alle erfassten Zeilen“ auch die Zeilen des `RecordingBackend` einschließen (Frage 1 in §6) — dann ist ein Eingriff in den Testserver nötig (zweites `main` ohne `RecordingBackend` oder die Tabelle `laufend` im Standard-`main`), und der Architect entscheidet nach `SPEC-040` und `SPEC-052`; (3) der Test lässt sich ohne Wanduhr als Erwartung nicht bauen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig (drei Liefer-Punkte mit Mutationsnachweis), `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung*, die Liste der Wartestellen mit ihren Signalen, die Laufzeit des Tests und den Steering-Loop-Lerneintrag. Mit diesem Slice in `done/` ist das „Mehr“ von [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) §3 durch **einen** benannten Test belegt; die Closure-Notiz der Welle nennt ihn dort.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht. Die Form jedes Ausgangs: *eingetreten* → Carveout oder Folge-Slice mit Kennung · *entfallen* → Grund · *weiter offen* → `BEO-<KUERZEL>/<slug>` im Register.

**Fragen an den Nutzer (vor dem Start zu beantworten; der Plan unterstellt die Antwort in Klammern):**

- **Frage 1 — Zeilen des `RecordingBackend`.** Der einzige Prozess mit einer Anfrage ohne Ende (`laufend`) und mit Tabellen für die parametrisierte Anfrage ist der Kompatibilitätsserver (`KompatibilitaetsServer.kt` Zeile 163); sein `RecordingBackend` schreibt je Anfrage eine Nicht-JSON-Zeile mit dem **SQL-Text** auf dieselbe Standardausgabe. Das Standard-`main` (`Main.kt` Zeile 37) kennt keine Anfrage ohne Ende. Alternativen: **(A, unterstellt)** der Lauf geht gegen den Kompatibilitätsserver, „alle erfassten Zeilen“ sind die JSON-Zeilen des Adapters (so sagt es §3 der Welle), die Zeilen des `RecordingBackend` werden einzeln geprüft und sind als Ausnahme im Test benannt — kein Produktcode, ein ehrlich benannter Rand; **(B)** ein Eingriff in den Testserver (die Tabelle `laufend` im Standard-`main`, oder ein `main` ohne `RecordingBackend`): dann stehen wirklich alle Zeilen unter der Inspektion, aber es ist Produktcode des Testservers, `SPEC-040` und `SPEC-052` sind zu schärfen und der Architect entscheidet (Rückführung in §4); **(C)** der Cancel bleibt aus dem Lauf und wird nur im Prozess des Tests belegt (`MetricsTracingWiringTest`) — das widerspricht dem Wortlaut von §3 der Welle und wäre Weg A, den der Nutzer verworfen hat.
- **Frage 2 — Kennung des Fehlerschritts.** Der Fehlerschritt ist „Fehler der Quelle“ (`kaputt`, Fehler mitten in der Antwort). Das Protokoll kann dabei anders enden als bei einem Fehler vor der ersten Zeile (Verbindung zu statt Fehlerantwort); die Vorprüfung in §3 (2) klärt es, fällt das Ergebnis auf „Verbindung zu“, nimmt der Test den Fehler der unbekannten Tabelle und führt den Befund als Beobachtung. Ob dem Nutzer ein Fehlerschritt „Fehler der Quelle“ zusätzlich zum Fehler der Tabelle wichtig ist, ist offen (unterstellt: nein, ein Fehlerschritt genügt).

**Risiken:**

- **Wanduhr-Flake.** `BEO-TDS/wanduhr-tests-flackern-unter-last` steht bei 8× (Stand im Register: offen, Schwelle erreicht, Ausgang beim Nutzer, nicht gesetzt; die Hypothese ist die Reihenfolge von Antwort, Ereignis und Beenden, `BEO-TDS/test-erwartet-reihenfolge-eines-wettlaufs`). Der Test ist ein Kindprozess mit Takt der Bibliothek (1 s), mehreren Verbindungen und SIGTERM — die Klasse, die dort rot wurde. Vorgabe: jeder Wartepunkt hängt an einem Ereignis (§3, „Bauart gegen die Wanduhr“), SIGTERM erst nach den Zeilen der Ruhe, der Cancel erst nach den ersten Bytes; Lastunabhängigkeit folgt aus dieser Konstruktion und wird an der Liste der Wartestellen in der Closure-Notiz belegt, **nicht** durch eine Messung unter Last und nicht durch künstliche Last. — **Ausgang:** *eingetreten* → roter Lauf außerhalb oder innerhalb des Diffs: Beleg als weitere Datei in `evidence/` des Eintrags, Folge-Slice mit Kennung | *entfallen* → die Wartestellen sind alle ereignisgesteuert und der Review bestätigt es (kein Lauf unter Last als Beleg) | *weiter offen* → `BEO-TDS/wanduhr-tests-flackern-unter-last` (kein neues Auftreten, Zähler unverändert).
- **Zahlen der Erwartung nicht gemessen.** Anzahl der Anfragen, `outcome`-Werte und die Reihenfolge der Enden der Spans stammen aus `spezifikation.md` §5 (Festlegung, nicht gemessen) und aus dem Test im Prozess (`MetricsTracingWiringTest`), nicht aus einem Lauf des Kompatibilitätsservers. — **Ausgang:** *eingetreten* → die Abweichung ist ein Befund über das Produkt (Rückführung §4) oder über die Spezifikation (Spec-Lücke in §7) | *entfallen* → die erwarteten Werte stehen am ersten Lauf | *weiter offen* → `BEO-TDS/aussagen-ueber-messung-hinaus`.
- **Redaktion sucht nur Bekanntes.** Der Test sucht Werte, die der Lauf kennt (Passwörter, Parameterwert, Text der Ausnahme); eine Kennung, die er nicht kennt, fände er so nicht. Gegenmaßnahme in §2 Punkt 2 (b): die Menge der erlaubten Felder je Zeile. Diese Menge dupliziert die Tabelle `PROPERTIES` in `TelemetryJson.kt` (Zeilen 50 bis 73; `src/test` sieht das Modul `testserver` nicht, `TelemetryJsonTest` liegt dort), eine zweite Fundstelle derselben Zahl. — **Ausgang:** *eingetreten* → ein Feld fehlt in der Menge nach einer Erweiterung des Adapters: der Test ist rot, die Menge wird in beiden Orten nachgezogen | *entfallen* → ein Sensor hält beide Mengen gleich | *weiter offen* → `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert` und `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor`.
- **Laufzeit der Stage `test`.** Ein weiterer Kind-JVM-Lauf mit Takt von 1 s, Fehler der Quelle, Cancel und dem Leeren der Warteschlange vor dem Prozessende (Frist 2 s, `SPEC-052`) verlängert `make test`; die Dauer ist nicht gemessen. Die Laufzeit des Tests (Gradle-Bericht, ein Lauf) steht in der Closure-Notiz, ohne Aussage über Last; die Ausführung gehört ins Gate, weil der Prozesstest der bestehenden Form in der Stage `test` liegt ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidung 4). — **Ausgang:** *eingetreten* → die Dauer ist für die Stage nicht tragbar: Nutzerentscheid, ob der Lauf in die Stage `smoke` oder nach `make e2e` wandert ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) ändert sich dadurch nicht, Folge-Slice mit Kennung) | *entfallen* → Dauer genannt und vom Nutzer getragen | *weiter offen* → Beobachtung im Register mit der Zahl.
- **Cancel am Prozess bisher unbelegt.** ATTENTION während einer laufenden Anfrage ist am Serverprozess nicht gemessen (nur im Prozess des Tests und über SIGTERM am Prozess). Bleibt die Antwort aus, ist es ein Befund über das Produkt, kein Fehler des Tests. — **Ausgang:** *eingetreten* → Rückführung §4 (1) mit Folge-Slice | *entfallen* → die Antwort `DONE[ATTN]` steht am Prozess | *weiter offen* → Beobachtung im Register.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt `src/test` (und gegebenenfalls `docs/plan/spec-abdeckung.md`), keine eigene Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 166 Einträge), nachgesehen am 2026-11-13; Treffer:

- `BEO-TDS/wanduhr-tests-flackern-unter-last` — offen, 8×, Ausgang beim Nutzer, nicht gesetzt: Risiko in §6, Bauart in §3.
- `BEO-TDS/test-erwartet-reihenfolge-eines-wettlaufs` — Klasse der Reihenfolge (Beenden überholt das Ereignis): Vorgabe „SIGTERM erst nach den Zeilen der Ruhe“ in §3.
- `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge` — die Mutationen in §2 sind so gewählt, dass sie nicht von einer Reihenfolge nebenläufiger Threads abhängen (kein Ziel nach Nummer).
- `BEO-TDS/redaktion-sucht-nur-den-bekannten-wert` — Risiko in §6 (Menge der erlaubten Felder).
- `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` — offen: die Menge der Felder steht in `TelemetryJson.kt` und im Test (Risiko in §6).
- `BEO-TDS/backend-ueber-tablestore-meldet-kein-bereit-ereignis` — betrifft nur `simulation_backend_ready`; die Spans der Ebene `DEBUG` sind davon nicht berührt (Vorprüfung §3 (4)).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — verkörpert: ein grüner gecachter Lauf der Stage `test` belegt keine Wiederholung; die Wiederholung belegt der Verifier mit `make clean-build`, wo die Tabelle für `src/` es vorsieht (dieser Slice ändert nur `src/test`; die Tabelle der Änderungsarten nennt für Tests nur `make spec-abdeckung` einmal).
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 3×, geplant bei [slice-qa-sicherheit](../done/slice-qa-sicherheit.md) (Loginspektion des minimalen Logs); die Gesamtinspektion am Prozess ist das Thema dieses Slice, keine neue Zahl.
- Ohne Treffer: die übrigen Beobachtungen. Keine erreicht mit diesem Slice 3×.

Alle berührten Sub-Areas GF.
