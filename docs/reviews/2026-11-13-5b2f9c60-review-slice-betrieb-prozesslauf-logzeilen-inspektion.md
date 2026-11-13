# Review-Report: slice-betrieb-prozesslauf-logzeilen-inspektion — 2026-11-13

**Review-Art:** Code (gegen Plan, Verträge und Hard Rules; nicht gegen die DoD)

**Gegenstand:** `slice-betrieb-prozesslauf-logzeilen-inspektion`, Diff `0f52bb66..5b2f9c60` (Commits 4e0c86d6 Plan §3, fd49abd2 Test und Testhilfe, 5b2f9c60 Matrizen und Text). Der Zweig ist gegenüber `main` (`e7ca8929`) im Rückstand (Merge-Basis `0f52bb66`); die zehn Commits von `main` betreffen `referenztest-dotnet/Dockerfile`, `spec/spezifikation.md`, `tools/mk/vergleichslauf-d.mk`, Closure und Register eines anderen Slice und berühren keine Datei dieses Diffs.

**Skill:** `.harness/skills/reviewer.md` @ 5b2f9c60
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-13

**Eingangs-Kontext:**

- `slice-betrieb-prozesslauf-logzeilen-inspektion` (§1, §2, §3 samt fortgeschriebener Vorprüfung, §4, §6) und `welle-betrieb-diagnose` §3
- [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (Ereignismodell, Format der Zeile, Bereitschaftszeile), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Entscheidung 4: Prozesstests der Stage `test`)
- `SPEC-017` bis `SPEC-019`, `SPEC-043`, `SPEC-048`, `SPEC-052`; `TDS-FA-09`, `TDS-QA-03`
- `AGENTS.md` §3.2, §3.7, §3.8; `harness/README.md` §Minimal agent workflow

**Sensoren dieses Laufs** (Docker, Wegwerf-Stage `rev` auf der Stage `deps` des `Dockerfile`, `gradle :test --tests …TestserverLaufInspektionTest`, ein Gradle-Lauf zugleich, Vordergrund bzw. beobachtet, keine künstliche Last; Mutationen nur im Container, der Arbeitsbaum blieb unverändert):

| Lauf | Ergebnis | Wanduhr |
|---|---|---|
| Ausgangsstand | grün, Test 4,7 s im Gradle-Bericht | 64 s und 68 s (Bau der Stage dominiert) |
| M1 `DONE[ATTN]` nicht gesendet (`AbortedAnswer`, Zeile 46) | rot: „die Antwort endet mit DONE und ATTENTION" | 77 s |
| M3 `duration_ms` aus `TelemetryJson.PROPERTIES` entfernt | rot: „Pflichtfelder [duration_ms] fehlen in … tds.connection" | 80 s |
| M4 `request_id` + `x` (`MeasurementEvents`, Zeile 37) | rot an der Gleichheit von `request_failed` und Ende von `tds.query` | 75 s |
| M10 (nicht berichtet) Passwort der Ablehnung in `cause` von `login_rejected`, Großschreibung | rot: „die Ausgabe nennt 'falsch-pw-z3'" (Suche ohne Beachtung der Schreibweise) | 67 s |
| M11 (nicht berichtet) `x-PBKDF2-y` in `cause` von `login_accepted` | rot: „die Ausgabe nennt 'pbkdf2'" | 67 s |
| M12 (nicht berichtet) `connection_closed` wird nicht geschrieben | rot: „connection_closed: …" nach der Schranke von 60 s (Wanduhr 134 s) | 134 s |
| M14 (nicht berichtet) `request_kind` stets `sql_batch` | rot an der Liste der Arten (`rpc` fehlt) | 80 s |
| M15 (nicht berichtet) beliebige Base64-Zeichenkette (`c2FsdA-600000-aGFzaA`) in `cause` von `login_rejected` | **grün** | 65 s |

Lesen und Bauen zusammen etwa 25 Minuten. Nicht gefahren (Tabelle in `harness/README.md`): `make clean-build`, `make e2e`, `make gates`; `make docs-check` grün (1410 Dateien, 0 Befunde).

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Der KDoc des Tests und §2 Punkt 2 (c) des Plans sagen „weder Prüfwert …", gesucht wird aber nur die Marke `pbkdf2`; der Prüfwert `{pbkdf2-sha256}Salz$Iterationen$Hash` wird im Test inline berechnet und nicht als Ganzes gesucht. Failure-Szenario: ein Adapter schreibt den zerlegten Prüfwert (Salz, Iterationen, Hash ohne Marke) in `cause`, `reason` oder `failure`; Namen sind in `EVENT_FIELDS` erlaubt, `ONLY_IN` beschränkt diese Felder nicht auf Arten, und der Test bleibt grün (M15 gefahren). | `TDS-QA-03`, `AGENTS.md` §3.8 | `TestserverLaufInspektionTest.kt` · „weder Passwort noch Prüfwert noch Kennung der Ablehnung"; `LogInspektion.kt` · `ONLY_IN` | ja — Mutation M15 (grün statt rot) | Redaktion sucht nur den bekannten Wert |
| F-2 | MEDIUM | Die Tabelle in Plan §3 nennt `docs/user/referenz-ereignisse.md`, `docs/plan/e2e-abdeckung.md` und `tools/e2e.sh` nicht, die der Diff berührt; §2 sagt zugleich „`docs/user/` ist nicht Gegenstand (kein Produktcode)". Failure-Szenario: wer §3 und §8 als Pfad-Kandidaten liest, schließt die Nutzer-Doku aus; die Änderung an `docs/user/` (und der Text, den `make e2e-abdeckung` aus `tools/e2e.sh` erzeugt) entsteht ohne Plan-Zeile und ohne vorgesehene Prüfung der Seite als Ganzes. | Maintainability (Reviewer-Skill: Plan-Tabelle) | `slice-betrieb-prozesslauf-logzeilen-inspektion` §2 · „`docs/user/` ist nicht Gegenstand"; §3 Tabelle | nein — Abgleich Plan gegen Diff | Plan-Tabelle nennt die berührten Dateien nicht |
| F-3 | LOW | Die Schranke von `ServerProcess.awaitIndex` ist 60 s, die Voreinstellung `query_timeout` des Laufs 30 s (`config_effective`). Failure-Szenario (nicht gefahren): bleibt die Zeile `metrics` mit `backend_active` 1 länger als 30 s nach `sendBatch` aus, beendet der Server die offene Anfrage selbst, und der Test wird rot an „DONE und ATTENTION", nicht an der ausbleibenden Zeile; unter üblicher Last liegt die Zeile nach 1 bis 2 s. Alle Wartestellen sind sonst Signale (Zeile, Bytes) mit Zeit nur als Schranke. | Plan §3 „Bauart gegen die Wanduhr", [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) | `TestserverLaufInspektionTest.kt` · „keine offene Anfrage"; `ServerProcess.kt` · `WAIT_SECONDS` | nein — nur unter Starvation von über 30 s | Wanduhr-Frist im Test |
| F-4 | LOW | §2 Punkt 2 (a) („`tdswire: gestartet, Port <n>` zuerst") und Punkt 3 („die vier gestellten Anfragen") stehen im Plan unverändert neben der Abweichung in §3 (ii) und (i). Der Code ist richtig ([ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md): die Bereitschaftszeile folgt, „nachdem `StartOutcome` den laufenden Server geliefert hat"; davor stehen vier synchron geschriebene Ereignisse); das Finding gilt dem Plan, nicht dem Code. | [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (Bereitschaftszeile), Plan §2/§3 | `slice-betrieb-prozesslauf-logzeilen-inspektion` §2 · „`tdswire: gestartet, Port <n>` zuerst" | nein | Plan-Wortlaut nicht an Vorprüfung angeglichen |
| F-5 | LOW | Der Plan-Commit 4e0c86d6 steht vor dem Test-Commit fd49abd2, das „Ergebnis der Vorprüfung" entstand aber an der Lauf-Ausgabe des fertigen Tests (der Text nennt `gradle :test --tests …TestserverLaufInspektionTest`, nicht aber, dass der Code davor stand). Die Zusage „erster Schritt, vor dem Bau" und die Regel „Planänderung vor dem Code" sind damit für die Hypothesen 1 bis 4 nicht geprüft worden; keine fiel, der Befund ist unverändert. | Plan §3 „Vorprüfung vor dem Bau", „Planänderung statt Überspielen" | `slice-betrieb-prozesslauf-logzeilen-inspektion` §3 · „Ergebnis der Vorprüfung (Implementer, 2026-11-13; gefahren in der Stage-`test`-Umgebung" | nein | Vorprüfung nach dem Code |
| F-6 | LOW | `LogInspektion` führt Pflicht- und erlaubte Felder je Art als Kopie von `TelemetryJson.PROPERTIES` und `EffectiveSettings`; die Kopplung ist im KDoc benannt und der fehlende Sensor ebenfalls („diese hält nichts"). Der KDoc des Tests spricht von „den Feldern der Spezifikation", die Menge enthält aber Namen, die `spec/spezifikation.md` nicht führt (`issue_kind`, `exception_type`). Die Richtung neues Feld im Adapter → rot ist gefahren (M3 spiegelbildlich, M11); Rot bei verlorenem Pflichtfeld (M3) und vertauschtem Wert (M14) ebenfalls. Lax: ein Feld der Menge, das der Adapter nie schreibt, bleibt unbemerkt. | `SPEC-052`, Beobachtung `zahl-an-mehreren-orten-ohne-sensor` | `LogInspektion.kt` · „diese hält nichts"; `TestserverLaufInspektionTest.kt` · „kein Feld liegt außerhalb der Felder der Spezifikation" | ja — M3, M11, M14 | Zweite Fundstelle ohne Sensor |
| F-7 | INFO | Umgebung: Menge = 4 × (`tcp_wmem[2]` + `tcp_rmem[2]`); im Container der Stage (`gradle:8.14-jdk21`) gelesen 4 194 304 und 6 291 456, also 40 MiB. Auf einem Host mit großen Puffern (Beispiel 64 MiB je Obergrenze, 512 MiB) bricht der Test mit „Datenmenge … über der Grenze" ab, rot ohne Fehler des Produkts; grün ohne offene Anfrage ist nicht möglich, weil `backend_active` 1, `outcome` `cancelled` und „Antwort kleiner als die Menge" zugleich verlangt werden. Das Risiko „Datenmenge, Puffer, Heap" ist im Plan §6 benannt. Zeiger an den Verifier/Planner. | [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) | `TestserverLaufInspektionTest.kt` · „Datenmenge $volume über der Grenze" | nein | Annahme über Puffer der Umgebung |
| F-8 | INFO | Die Verbotswörter sind Teilstrings ohne Beachtung der Schreibweise (M10, M11 rot). Streng: `select`, `insert`, `zz_` würden auch eine Marke wie `BULK_INSERT_…` im Feld `reason` röten. Lax: Teile eines Passworts oder Parameterwerts, einzelne Wörter des Fehlertextes. Beides ist mit dem Zweck („Bekanntes suchen") vereinbar. | `TDS-QA-03` | `TestserverLaufInspektionTest.kt` · `"select",\n "insert"` | ja — M10, M11 | Redaktion sucht nur den bekannten Wert |
| F-9 | INFO | Die Zahl „1 225 136 von 41 943 040 Byte" in der Vorprüfung stammt aus einer Ausgabe, die der Test nicht schreibt (die Byte-Zahl steht nur in der Meldung der Zusicherung); die Aussage trägt als Größenordnung („ein Lauf, kein Dauerbeleg" steht dabei). | `AGENTS.md` §3.8 | `slice-betrieb-prozesslauf-logzeilen-inspektion` §3 · „Gelesen wurden bis dahin 1 225 136" | nein | Messzahl aus einem Erzeuger, der nicht im Repo liegt |

### Antworten auf die Prüfpunkte des Auftrags

1. **Lastunabhängigkeit.** Die Wartestellen der Quelle sind Signale: `awaitIndex` auf `metrics` (Ruhe: `requests_total` gleich Zahl der gesendeten Anfragen, `spans_open` 1, `backend_active` 0), danach auf die nächste Zeile mit `backend_active` 1 ab dem Index nach der Ruhezeile, `settle` auf Ende beider `tds.connection`, `metrics` mit `spans_open` 0 und allen Anfragen, `connection_closed`, vor SIGTERM; Antworten über Bytes. Kein Rennen gefunden, das Rot oder Grün aus falschem Grund erzeugt: `requests_total` zählt beendete Anfragen, die Ruhezeile kann nicht vor Ende der dritten Anfrage stehen; der Zustand „ungelesene Antwort, Schreiber blockiert" bleibt stehen, die Zeile mit `backend_active` 1 kommt spätestens nach einem Takt; eine Zeile `metrics` zwischen Ruhezeile und Senden hat `backend_active` 0 und wird übergangen. Endet die Anfrage vor ATTENTION (Puffer zu groß), werden Rot an „keine offene Anfrage" bzw. „Antwort … kleiner als" erzwungen, nie Grün. Einzige Wanduhr-Kopplung: F-3.
2. **Umgebung.** Trägt im Container der Stage (Werte oben); F-7 für abweichende Hosts. Der Test wird nie grün, ohne die Zusage zu belegen (Anfrage offen, `cancelled`, Antwort abgebrochen: alle drei geprüft).
3. **Rot aus dem richtigen Grund.** M1, M3, M4 bestätigt wie berichtet; M10, M11, M12, M14 zusätzlich rot an der jeweils benannten Behauptung; M15 grün (F-1). Zählung `connection_closed`: ohne die Zeile wartet der Test die volle Schranke von 60 s ab und meldet „connection_closed: …" mit der Ausgabe; `span_ended` der Verbindung wird in `settle` und in `inspectSpans` verlangt (nicht gefahren, im Code gelesen).
4. Siehe F-6.
5. Siehe F-4 und F-5: „zuerst" ist ein Defekt des Plans, nicht des Codes.
6. Jede Zusage des Plans §2 hat einen Test, der ohne den Fix aus dem richtigen Grund rot wird (M1 bis M4, M10 bis M14); die Zusage „Prüfwert" nur mit der Marke (F-1). `docs/user/referenz-ereignisse.md` Zeile 78 nennt Passwort, Parameterwert und SQL-Text (nicht den Prüfwert) und „Menge der Felder": trägt höchstens so weit wie der Beleg, sobald F-6 beachtet ist. Der Zusatz in `docs/plan/e2e-abdeckung.md` Zeile 23 und `tools/e2e.sh` Zeile 35 steht im Satz der Grenzen („Nicht belegt: … die Zeilen des Log-Adapters über die genannten Ereignisse hinaus (… TestserverLaufInspektionTest in make test)") und ist textgleich in beiden Dateien; `docs/user/referenz-ereignisse.md` ist nach den Wörtern der Skill-Liste (bisher, noch nicht, nicht gebaut, Zielzustand, vorläufig, soll, seit Slice) durchsucht: kein Treffer.
7. Kommentare und KDoc tragen Zusage, Kopplung oder Grenze; kein `@Suppress`, `noqa`, `nolint` im Diff. `RawClient.receiveBufferBytes` (Voreinstellung 0) und `ServerProcess.awaitIndex` (neu) ändern bestehende Aufrufe nicht. Der Test liegt in `src/test`, nicht in der Stage `smoke` (Stage unverändert). Laufzeit: 4,7 s im Gradle-Bericht, gemessen ohne Aussage unter Last; sie verlängert die Stage `test` nicht merklich.
8. Kein Diff in `src/main` und `testserver` (`git diff --stat 0f52bb66..HEAD -- src/main testserver` leer): bestätigt; öffentliche Fläche unverändert. `docs/plan/spec-abdeckung.md` führt `TestserverLaufInspektionTest` bei `SPEC-017`, `SPEC-018`, `SPEC-019`, `SPEC-052` mit um 1 erhöhten Zählern; der Kopf („49 von 59") bleibt, weil keine Kennung neu bestanden hat.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main`, `testserver/` (Produktcode) | geprüft, ohne Befund (kein Diff) |
| `src/test/…/runtime/RuntimeSupport.kt`, `ServerProcess.kt` (Testhilfe) | geprüft, ohne Befund |
| `src/test/…/runtime/LaufClient.kt` (`BulkWire`, `LaufClient`) | geprüft, ohne Befund |
| `docs/plan/spec-abdeckung.md` | geprüft, ohne Befund |
| `docs/plan/e2e-abdeckung.md`, `tools/e2e.sh` | geprüft, ohne Befund (Text gleich; Grenzen im selben Satz) |
| `docs/user/referenz-ereignisse.md` | geprüft, ohne Befund (siehe F-6 zur Reichweite der Aussage) |
| `docs/plan/planning/in-progress/slice-…` §6 (Risiken) | geprüft, ohne Befund (Ausgänge offen bis zur Closure; Haken der DoD stehen nicht als `- [x]`) |
| Suppression und Schichtregeln (`@Suppress`, `noqa`, `nolint`) im Diff | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 4 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Redaktion sucht nur den bekannten Wert (F-1, F-8) · Plan-Tabelle nennt die berührten Dateien nicht (F-2) · Wanduhr-Frist im Test (F-3) · Plan-Wortlaut nicht an Vorprüfung angeglichen (F-4) · Vorprüfung nach dem Code (F-5) · Zweite Fundstelle ohne Sensor (F-6) · Annahme über Puffer der Umgebung (F-7) · Messzahl aus einem Erzeuger, der nicht im Repo liegt (F-9)

## Verdikt

**Merge-blockierend:** ja, wegen der zwei MEDIUM (F-1: die Zusage „Prüfwert" ist breiter als der Beleg; F-2: Plan-Tabelle). Kein HIGH; die Konstruktion des Tests (Signale statt Zeit, Rückdruck mit dreifacher Absicherung gegen falsches Grün) trägt und ist in sieben von acht gefahrenen Mutationen aus dem richtigen Grund rot.

**Übergabe:** Findings an den Implementer; die Finding-Klassen gehen in die Slice-Closure §7. Der Report ersetzt keine Verifikation (DoD, `make clean-build`, `make e2e` gehören dem Verifier).
