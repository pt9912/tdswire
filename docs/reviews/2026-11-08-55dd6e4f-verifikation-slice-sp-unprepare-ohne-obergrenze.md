# Verifikationsbericht: slice-sp-unprepare-ohne-obergrenze (HEAD 55dd6e4f)

Rolle Verifier (Modul 11): DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Zweig `worktree-agent-a7bc3c771ae8ca3bb`, Basis `d18a8541`, `main` bei `66fee6c1`. Bezug: [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md). Slice: `docs/plan/planning/done/slice-sp-unprepare-ohne-obergrenze.md`. Reviews: `1645b7c2`, `ebd197da`, `5c518231` (alle unter `docs/reviews/`). Der Verifier ändert keinen Produktivcode und setzt keine Haken.

## Gesamturteil

Die Mechanik und die Entscheidungen tragen: Obergrenze 100 ist aufgehoben (Mutation rot), die Antwort läuft gestreamt unter `AbortableRun`, es gibt genau einen Abschluss je Anfrage, alle Gates, E2E, `matrix-zitate`, `flaeche`, `docs-check` und der frische Bau sind grün. **Liefer-Punkt 1 (Spezifikation und Antwort) ist belegt. Liefer-Punkt 3 (Nachzug) ist belegt bis auf eine LOW-Lücke (V-2). Liefer-Punkt 2 (Schutz nach [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)) ist beim Heap belegt, bei der Zeit nicht** (V-1, MEDIUM): die DoD verlangt „Messung mit Zahlen (Zeit, Heap)“; für den gebauten Strom-Stand steht keine Zeitzahl im Repo. Die Aussagen in `SPEC-060`, Nutzer-Doku und Matrix gehen nicht über den Beleg hinaus (die Zeit wird dort nicht behauptet). Haken: keine gesetzt (geprüft: alle Kästchen des Slice sind `[ ]`).

Befunde: 0 HIGH, 1 MEDIUM, 1 LOW, 4 INFO.

## Sensor-Belege (selbst gefahren, im Vordergrund, auf HEAD `55dd6e4f`, Worktree sauber)

Last des Hosts während der Läufe: Load Average 8 bis 160 (parallele Läufe anderer Rollen).

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` (einmal, `--no-cache`: Jar, Tests, detekt) | Exit 0; 1202 Tests `PASSED`, keine roten; detekt, `testserver:detekt`, Einbettungstest grün; `UnprepareLastProcessTest > Zwei gleichzeitige Batches …` PASSED, `ConnectionStormTest` PASSED (kein Flake) | 15 min 19 s |
| `make gates FORCE=1` | Exit 0; alle 16 Gates durchlaufen (detekt-selbsttest, kopplung-zahlen, skript-selbsttest, detekt, build, referenztest-dotnet, test, vier Stempel-Selbsttests mit 118/95/101/67 Fällen, docs-check 1239 Dateien 0 Befunde, flaeche, gate-zuordnung 16 Gates/33 Fälle, stempel-eingaenge) | 40 s (alle Docker-Stages aus dem Layer-Cache des Frischbaus, siehe INFO V-6) |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung); im Diff von `src/main` keine neue öffentliche Deklaration, `src/integrator` und `testserver` unberührt | wenige s (Cache) |
| `make docs-check` | `d-check: 1239 Datei(en) geprüft, 0 Befund(e)` | wenige s |
| `make matrix-zitate` | 1202 bestandene Testfälle, 307 verschiedene Zitate, 0 ohne PASSED, 0 nicht gelesene Formen | 7 min 4 s |
| `make e2e` | Exit 0, 8 Stufen grün: 80 / 190 / 66 / 61 / 27 / 36 / 55 / 21 s; kein Last-Flake, Stufe 7 nicht wiederholt | 536 s |
| d-migrate-Lauf | nicht gefahren; der Slice verlangt ihn nicht | – |

## Mutationen (selbst gefahren, einzeln)

Kopie des Baums bei `HEAD` in eigenem Scratchpad-Ordner, Stage `test` begrenzt auf `UnprepareDispatchTest`, `UnprepareLastProcessTest`, `PreparedHandlesDispatchTest`, `ErrorMapperTest` (57 Tests; für m6 zusätzlich `CancelTimeoutTest`, `ShutdownDispatchTest`, 66 Tests). Grundlauf ohne Mutation: grün (57 Tests).

| Nr. | Mutation | Wirkung |
|---|---|---|
| m1 (R2) | `UnprepareBatch.release`: `.take(100)` | rot: `UnprepareDispatchTest > Jede unbekannte Nummer ergibt einen ERROR, das Log einen Eintrag je Batch` und `UnprepareLastProcessTest > Zwei gleichzeitige Batches …`; 2 von 57 |
| Puffer (R2 m2) | `StreamedResponse.accept` hält die Bytes bis zum DONE und schreibt sie in einem Zug (`reply.write(encoder.toByteArray())`) | rot im Prozesstest (`UnprepareLastProcessTest`, Zeile 103); die Mutation färbt zusätzlich 14 weitere Tests von `PreparedHandlesDispatchTest` und anderen, weil sie das Antwortformat jeder Anfrage ändert; der Prozesstest ist trotzdem rot |
| Liste (eigene Variante) | `unknownHandles` sammelt alle Tokens in einer `ArrayList` und schreibt danach | **grün** (66 Tests), siehe V-3 |
| m1 (R3) | `reportUnknown`: `run.run(null)` statt `run.run(remaining)` | rot: `UnprepareDispatchTest > Der Ablauf der Frist waehrend der Antwort der Freigabe bricht sie mit TDS_TIMEOUT ab`; 1 von 57 (R3 F-1 behoben) |
| m4 (R3) | `resetPending = false` in `reportUnknown` gestrichen | rot: `UnprepareDispatchTest > ATTENTION nach der vollstaendigen Antwort der Freigabe wiederholt das ENVCHANGE nicht`; 1 von 57 (R3 F-2 behoben) |
| m5 (R3) | `answer`: `unknownHandles(...) \|\| true` (Schreibfehler ignoriert) | rot: `UnprepareDispatchTest > Ein abgewiesenes Token mitten in der Antwort beendet die Verbindung ohne weitere Antwort`; 1 von 57 |
| m6 (R3) | `RunConclusion`, Zweig `Abandoned`: `abandon(CANCEL_TIMEOUT)` entfernt | in den vier engen Klassen grün (57 Tests, wie der Review 3 erwartet: dort tragen die Tests des Cancel nicht); mit `CancelTimeoutTest` und `ShutdownDispatchTest` rot, 3 von 66 |

Eine erste Fassung des Mutationslaufs lag in einem Scratchpad-Ordner, der schon Dateien anderer Rollen trug (V-6); ich habe alles in einem eigenen Ordner (`vfy-spu-a7bc`) wiederholt, die Ergebnisse oben sind die der Wiederholung.

## Zustandsmaschine der Freigabe-Antwort

Gelesen in `RequestLoop.reportUnknown` und `RunConclusion`: die Antwort läuft unter `AbortableRun` mit der Frist der Anfrage; `RunConclusion.conclude` ist die herausgelöste Logik von `execute` (Zweige `Finished`, `Cut`, `Abandoned`, `Disconnected`) und liefert je Anfrage genau ein Ereignis. ATTENTION mitten in der Antwort endet sie mit genau einem `DONE[ATTN]` (Test `ATTENTION waehrend der Antwort …`, rot unter der Mutation ohne `AbortableRun`, R3 m2); `QUERY_TIMEOUT` mitten in der Antwort endet mit ERROR 50005 und DONE mit Fehlerstatus (Test mit gesteuerter Zeit, rot unter m1 R3); ATTENTION nach dem vollständigen Abschluss lässt die Antwort vollständig und sendet `DONE[ATTN]` als eigene Nachricht, das ENVCHANGE steht nur einmal als erstes Token (Test `ATTENTION nach der vollstaendigen Antwort …`, rot unter m4). Ein Schreibfehler an jeder Stelle beendet die Verbindung ohne weitere Antwort (rot unter m5). Die Lage des ENVCHANGE als führendes Token ist in `UnprepareDispatchTest` per Handvektor gehalten. RPC, Batch und Bulk laufen unverändert über dieselbe `RunConclusion`: die 1202 Tests des Frischbaus (darunter `AttentionCancelTest`, `CancelTimeoutTest`, `ShutdownDispatchTest`, die unter m3/m6 rot werden) und die acht E2E-Stufen sind grün.

## DoD-Zeile für DoD-Zeile

| DoD-Zeile | Beleg | Urteil |
|---|---|---|
| LP1: `SPEC-060` ohne Obergrenze und ohne Begrenzung des Logs je Batch | Diff `spec/spezifikation.md`: „je nicht gehaltener Nummer … ohne Obergrenze“, Log „einmal je Antwort“ (Setzung) | erfüllt |
| LP1: Messvermerk M-22 (100/101/150 Draht, bis 2000 Client) mit Grenze „Einzelmessung je Instanz“; „Setzung, nicht gemessen“ entfällt; Auflösungs-Trigger als erfüllt vermerkt | `SPEC-060`, Absatz „Messvermerk 2026-11-07 … ist damit erfüllt“ | erfüllt |
| LP1: Bibliothek meldet je unbekannter Nummer einen Fehler; Tests 99, 100, 101, 150, 2000 | `UnprepareDispatchTest > Jede unbekannte Nummer ergibt einen ERROR, das Log einen Eintrag je Batch` (PASSED im Frischbau, rot unter m1) | erfüllt |
| LP2: Träger begründet (Optionen 1 bis 6 im Plan) | Plan §3, Optionen; Wahl 2 + 4 | erfüllt |
| LP2: Batch mit mindestens 2000 Nummern: begrenzter Speicher, Verbindung offen, Folgeabfrage gelingt | Dispatch-Test mit 2000 Nummern, Handle 1 danach frei; Prozesstest: zwei Nachrichten von 16 MiB (419 000 Nummern) bei 256 MiB Heap, Prozess lebt, neue Verbindung wird beantwortet | erfüllt (Heap) |
| LP2: begrenzte **Zeit**, „Messung mit Zahlen (Zeit, Heap)“ | im Messprotokoll keine Zeitzahl; die „3,6 s“ in Plan §3 Option 6 stehen nicht im Messprotokoll (Reihe a hat keine Zeitspalte) und gelten dem Puffer-Stand | **nicht belegt**, V-1 |
| LP2: neue Konfiguration oder öffentlicher Name mit Rückführung | keine neue Konfiguration, kein neuer öffentlicher Name (`make flaeche` grün, Diff nur `internal`) | erfüllt (entfällt) |
| LP3: Nutzer-Doku und Matrix nennen die Obergrenze nicht mehr als Grenze | `docs/user/referenz-konfiguration.md`, `docs/plan/kompatibilitaetsmatrix.md` (Diff); Schutz mit Grenze nur in der Nutzer-Doku (V-2) | erfüllt, mit V-2 |
| LP3: Register fortgeschrieben | `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab/state.md` („gebaut, Verifikation offen“, Anker auf Slice und Messprotokoll) und `messung-am-echten-sql-server-fehlt/state.md`; keine neue Evidenz-Datei | erfüllt |
| `make gates` grün | siehe Sensor-Belege (FORCE=1) | erfüllt |
| Review-Report liegt vor | drei Reports; der Stand nach Review 3 (`55dd6e4f`, nur Testcode, Plan, `SPEC-060`-Satz, Abdeckungsmatrix) ist nicht erneut reviewt, V-4 | erfüllt, mit V-4 |
| Haken nicht vom Implementer gesetzt | alle `[ ]` | erfüllt |
| Doku-Update bei öffentlichem Vertrag | Nutzer-Doku, Spezifikation, Matrix | erfüllt |
| Closure-Notiz, Lerneintrag | §7 „Wird bei der Closure gefüllt“ | offen, Planner |
| Jedes Risiko aus §6 trägt einen Ausgang | alle drei „offen bis zur Closure“ | offen, Planner |
| Drei Paarungen | Anker/Folge-Slice/Register: Closure-Sache; Register verweist auf den Slice im Pfad `in-progress/` | offen, Planner |

## Aussage ≤ Beleg

* **113 Byte / rund 125 Byte:** 113 Byte für die einstellige Nummer sind im Prozesstest als Konstante `ERROR_BYTES` gelesen und für 419 000 Nummern auf das Byte geprüft (47 381 930 Byte). Die 125 Byte der siebenstelligen Nummer folgen aus zwei Byte je Ziffer (UTF-16) und stehen als „rund“; die Probe b7 (siebenstellig) liegt nicht im Repo. Gedeckt, soweit „rund“ trägt.
* **b4 bis b8:** `SPEC-060`, Nutzer-Doku und Messprotokoll nennen b4 bis b6 als Messfassung außerhalb des Repos, b7 und b8 als Proben ohne Test, und „wiederholbar im Repo ist allein b2“. Gedeckt.
* **Prozessende bei vier Nachrichten:** in `SPEC-060` und Nutzer-Doku als Einzelmessungen mit der Option `-XX:+ExitOnOutOfMemoryError` des Image des Testservers eingeschränkt (zwei von zwei Läufen der Probe, ein Lauf von zwei der Messfassung; ohne Option drei von vier Antworten). Gedeckt.
* **Zeit des Strom-Standes (Frage aus Review 2 I-3):** `SPEC-060` und Nutzer-Doku behaupten keine Zeit; die Einschränkung ist insoweit erfüllt. Offen bleibt allein die DoD-Zeile des Slice (V-1).
* **ATTENTION/Fristablauf:** `SPEC-060` nennt die Bedingung („gewinnt ein ATTENTION vor dem Ende“, „Test mit gestelltem Auslöser am Fake-Kanal, am echten Kanal nicht gemessen“, „Test mit gesteuerter Zeit“). Gedeckt.

## Plan-vs-Code-Diff

Plan §3 gegen `git diff main...HEAD --stat` (31 Dateien): `UnprepareBatch.kt`, `RequestLoop.kt`, `RunConclusion.kt` (neu), `ErrorAnswers.kt` (neu), `ErrorMapper.kt`, `ResponseRun.kt`, `spezifikation.md`, Nutzer-Doku, Matrix, Messprotokoll, `spec-abdeckung.md`, die Testdateien (`UnprepareDispatchTest`, `ErrorMapperTest`, `UnprepareLastProcessTest`, `ServerProcess`, `RuntimeSupport`) und die Link-Nachzüge in Welle, Roadmap, vier Slices, zwei Registern, `referenzabgleich.md` stehen im Plan und im Diff. Im Diff, aber nicht namentlich im Plan: `PreparedHandlesDispatchTest.kt` (Test für das Freigeben des Handles bei abgebrochenem `sp_prepexec`, Review 3 I-1), und die drei Review-Reports (Ablage nach `docs/reviews/`). Im Plan, nicht im Diff: nichts. `StreamedResponse.kt`/`ReplyStream`, die der Plan in Option 4 nennt, sind unverändert und werden nur benutzt; eine Änderung des Zeilenprofils von `LeadingToken` meldet der Plan richtig als unverändert. Kein Produktivcode außerhalb der Plan-Zeilen. Keine neue öffentliche Fläche (`make flaeche`).

## Findings

### V-1 MEDIUM — DoD Liefer-Punkt 2 verlangt eine Zeitzahl; für den Strom-Stand gibt es keine

* quelle: Slice §2 LP2 („endet in begrenzter Zeit … Messung mit Zahlen (Zeit, Heap), kein Satz ohne Messzeile“), `AGENTS.md` §3.8, Frage des Verifiers aus Review 2 I-3
* pfad: `docs/messungen/unprepare-antwort-speicher-2026-11-07.md` (Reihen a und b ohne Zeitspalte); `docs/plan/planning/done/slice-sp-unprepare-ohne-obergrenze.md` §3 Option 6
* befund: Das Messprotokoll trägt für Reihe a und b keine Zeit. Option 6 im Plan nennt „3,6 s für 419 429 Nummern (Reihe a …)“; diese Zahl steht in Reihe a nicht, und sie gehört dem Puffer-Stand, der nicht im Repo liegt. Derselbe Absatz sagt, ob `QUERY_TIMEOUT` den Pfad begrenzt, sei „nicht untersucht“: das ist seit `ba1fdf3b` überholt (Freigabe-Antwort unter `AbortableRun`, Test mit gesteuerter Zeit rot unter m1 R3). `SPEC-060` und Nutzer-Doku sagen nichts über die Zeit und sind insoweit nicht überzogen; die DoD-Zeile ist nicht erfüllt. Eigene Zeit gemessen habe ich nicht (die Gate-Läufe liefen bei Load 8 bis 160, eine Zahl daraus wäre keine Messzeile).
* Ausgang für den Planner (eine von zwei): (a) eine Zeitzahl des Strom-Standes im Messprotokoll nachtragen (Test `UnprepareLastProcessTest`, Zeit je Verbindung, Last des Hosts nennen), Option 6 im Plan auf den Stand bringen; oder (b) die DoD-Zeile ausdrücklich auf „Heap und Abbruch durch Frist und ATTENTION, Zeit nicht gemessen“ zurücknehmen und die Grenze in §7 nennen.

### V-2 LOW — Kompatibilitätsmatrix nennt den Schutz und seine Grenze nicht

* pfad: `docs/plan/kompatibilitaetsmatrix.md`, Zeile „Freigabe der Handles“; Plan §3 Zeile „Nutzer-Doku, Matrix: … Schutz mit seiner Grenze genannt“
* befund: Die Matrix streicht die Obergrenze richtig und trägt M-22 und die Tokenfolge, nennt aber weder `MAX_MESSAGE_BYTES` als Träger noch die gemessene Grenze bei vier gleichzeitigen Nachrichten; nur die Nutzer-Doku und `SPEC-060` tun es. Die DoD-Zeile (Obergrenze nicht mehr als Grenze) ist erfüllt, die Plan-Zeile ist es nur für die Nutzer-Doku.

### V-3 INFO — Der Prozesstest fängt die Pufferung als Byte-Puffer, nicht als Tokenliste

* befund: Die Variante „alle ERROR-Tokens in einer Liste, danach schreiben“ (Mutation „Liste“) lässt alle 66 Tests grün; der Byte-Puffer (R2 m2) ist rot. Der Wächter trägt so weit, wie der Heap bei zwei Nachrichten von 16 MiB reicht; die Zusage „steht nie als Liste oder Puffer im Speicher“ in `SPEC-060` und im KDoc von `unknownHandles` ist durch den Test nur für den Byte-Puffer gehalten, für die Liste nur durch Lesen des Codes. Kein Befund gegen den Bau.

### V-4 INFO — Der Stand nach Review 3 hat keinen eigenen Review

* befund: `55dd6e4f` ändert nur Testcode (`UnprepareDispatchTest`, `PreparedHandlesDispatchTest`), Plan, einen Satz in `SPEC-060` und `spec-abdeckung.md`. Review 3 F-1 bis F-5 und I-1 sind darin behandelt; F-1 (Frist) und F-2 (`resetPending`) sind nach den Mutationen oben wirksam. Der Zuwachs ist klein und ohne Produktivcode; ein weiterer Review ist keine Bedingung dieser Verifikation, der Planner entscheidet.

### V-5 INFO — Merge-Probe gegen `main` (`66fee6c1`)

* befund: `git merge-tree --write-tree main HEAD` meldet 10 Inhaltskonflikte, alle in Dokumenten: `docs/messungen/referenzabgleich.md`, `docs/plan/planning/done/slice-katalog-und-struktur-an-messung-angleichen.md`, `done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md`, `in-progress/roadmap.md`, `observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md`, `next/slice-abnahme-d-migrate.md`, `done/slice-antwort-tokens-zielbild-luecken-bauen.md`, `open/slice-katalogspalten-smallint-tinyint-angleichen.md`, `welle-testserver-manuell.md`, `docs/plan/spec-abdeckung.md`. Kein Konflikt in `src/`, `spec/`, `docs/user/`, Matrix oder Messprotokoll. Das sind der Link-Nachzug des Lifecycle-Wechsels dieses Slice gegen den Lifecycle-Wechsel des Katalog-Slice auf `main`; `spec-abdeckung.md` ist nach dem Merge neu zu erzeugen (`make spec-abdeckung`).

### V-6 INFO — Verfahren: gemeinsamer Scratchpad und Cache

* befund (a): Der Scratchpad der Sitzung ist unter den Rollen geteilt und trug schon `mut`, `mutate.py`, `runmut.sh`, `mut-results.txt` und `mut-*.log`; ich habe diese Namen in einer ersten Fassung des Mutationslaufs überschrieben und danach alles in `vfy-spu-a7bc/` wiederholt. Frühere Inhalte dieser Dateien sind verloren; sie sind Wegwerfdateien anderer Läufe, aber der Zuschnitt „eigener Unterordner“ gilt auch für das Anlegen der Arbeitsordner. (b) `make gates FORCE=1` lief 40 s, weil alle Docker-Stages aus dem Layer-Cache des Frischbaus (`make clean-build`, gleicher Stand) kamen; der Frischbeleg der Tests liegt im `clean-build`, die Gates zeigen denselben Stand grün, nicht eine zweite Ausführung der Tests.

## Bedingungen für die Planner-Closure

1. V-1 auflösen: Zeitzahl des Strom-Standes ins Messprotokoll (mit Last des Hosts) und Option 6 im Plan nachziehen, oder die DoD-Zeile LP2 ausdrücklich auf „Zeit nicht gemessen“ einschränken und das in §7 als Grenze führen.
2. V-2 entscheiden: die Matrix um den Schutz (`MAX_MESSAGE_BYTES`, Einzelmessung bei vier gleichzeitigen Nachrichten) ergänzen oder die Plan-Zeile auf die Nutzer-Doku einschränken.
3. Die Haken der drei Liefer-Punkte und der Nachweis-Pflichten setzt der Planner nach diesem Bericht (Haken nicht gesetzt).
4. Closure-Notiz §7 mit Steering-Loop-Lerneintrag; die drei Risiken aus §6 je ein Ausgang (offen bis zur Closure; Belege: Größe der Nachricht begrenzt die Antwort, Streaming hält nur ein Paket; Messzeile des Schutzes b1/b2; Reihenfolge mit `slice-antwort-tokens-zielbild-luecken-bauen` ist gesetzt, Messreihe b nach deren Änderung neu zu messen).
5. Register-Paarung: `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab` von „gebaut, Verifikation offen“ auf den Stand der Closure setzen; `observation.md` trägt noch den Wortlaut „nicht gemessen“ (der Stand-Satz nennt ihn überholt).
6. Beim Merge nach `main` die zehn Dokumentkonflikte (V-5) auflösen und `docs/plan/spec-abdeckung.md` neu erzeugen; der Lifecycle-Wechsel nach `done/` folgt der Reihenfolge von `AGENTS.md` §3.3 (erst Inhalt, dann reiner `git mv`).
7. Optional: ein Review des Standes `55dd6e4f` (V-4); eine Tokenlisten-Mutation als Wächter-Grenze notieren (V-3).
