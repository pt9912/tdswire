# Verifikationsbericht: slice-sp-execute-unbekanntes-handle-bauen (HEAD 65d80508)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-sp-execute-unbekanntes-handle-bauen` (`in-progress/`, Zweig `worktree-agent-a59a0d30bb47a0dc7`, HEAD `65d80508`, `main` `a830d6d1` ist eingemergt; `git merge-tree` gegen `main`: Exit 0, konfliktfrei). Frage: Bauen wir es richtig, gegen Plan, DoD und Entscheidung.
* Maßstab: DoD §2 und Plan §3 des Slice, [ADR-0045](../plan/adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) (Accepted), [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), `SPEC-057`, `SPEC-060`, `SPEC-030`, `SPEC-022`.
* Verifiziert wird die Zusage, nicht der Diff. Am Produktivcode nichts geändert (Mutationen liefen in einer Kopie im Scratchpad). Haken habe ich nicht gesetzt; im Slice stehen 0 gesetzte Haken (`grep -c '\[x\]'` = 0).
* Der Review `2026-11-08-ba72fe81-review-slice-sp-execute-unbekanntes-handle-bauen.md` deckt `ba72fe81` (0 HIGH, 1 MEDIUM, 2 LOW). Die Nachbesserung `65d80508` hat keinen eigenen Review; sie ist unten einzeln geprüft (4 Dateien, 11 Zeilen +, 5 -: Plan-Tabelle, Nutzer-Doku, ein Satz in `SPEC-057`, ein KDoc-Kommentar in `ErrorMapper.kt`; kein ausführbarer Code).

## Eigene Läufe (Belege)

Host: Lastmittel 14 bis 28 durch fremde Läufe (kein ruhiger Host), Einzelmessungen, keine künstliche Last.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (einmal, End-Stand `65d80508`) | Exit 0, `BUILD SUCCESSFUL`; 1298 Testzeilen `PASSED`, 0 rote; 674 s. `ListenerAbortTest` (alle 5 Fälle, auch der bekannte Wanduhr-Flake) in diesem Lauf PASSED |
| `make e2e` | Exit 0, 8 Stufen grün, 376 s; Stufe 7 nicht rot, keine Wiederholung nötig |
| `make gates FORCE=1` | Exit 0 (d-check 1309 Dateien, 0 Befunde; Gate-Zuordnung 33 Fälle; Stempel-Selbsttests grün). Cache-Falle: Docker-Schichten überwiegend aus dem Cache gleicher Quellen; die Frische der Tests trägt `clean-build` |
| `make flaeche` | Exit 0 (Selbsttest und Prüfung) |
| `make docs-check` | Exit 0, 1309 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| `make matrix-zitate` | nicht gefahren (Vorgabe). Stattdessen `tools/matrix-zitate.sh` auf das Log meines `clean-build`: 1298 bestandene Testfälle, 344 Zitate, 0 ohne PASSED, 0 Fremdformen. Die Matrix-Datei hat gegen `main` genau eine geänderte Zeile (`sp_execute`); `65d80508` änderte keine Zitate. Alle fünf Zitate der Zeile stehen einzeln als PASSED im Log |
| Mutationen | 4 gefahren, alle rot (siehe unten) |

## DoD Zeile für Zeile

**Liefer-Punkt 1, Spezifikation: erfüllt.**
* `SPEC-057` (Zeile §4): Code `TDS_SIM_UNKNOWN_PREPARED_HANDLE`, Nummer 8179, Class 16, State 4, Text mit Nummer, Tokenfolge, offene Verbindung, Backend nicht gerufen, Log-Marke `unknown-handle`; „Behandlung nicht entschieden“ entfällt; Schluss „`TDS_UNKNOWN_HANDLE` (Nummer 50009) gibt es nicht mehr“.
* `SPEC-060` und Zeile `TDS_SIM_UNKNOWN_PREPARED_HANDLE`: State je Aufrufform (8 Batch-Freigabe, 4 `sp_execute`), Herkunft M-4, M-6, M-7. Zeile `TDS_UNKNOWN_HANDLE` entfernt, State-Ausnahmensatz um State 4 ergänzt, Änderungsprotokoll-Zeile 2026-11-08 vorhanden.
* `SPEC-030` (RPC-Tabelle `sp_execute`, Satz zum Fehlerfall, Absätze `sp_execute` und `RESETCONNECTION`) und `SPEC-022` („RPC im Backend-Vertrag“) nachgezogen.
* Zweite Fundstelle `docs/user/referenz-konfiguration.md`: Fehlertabelle ohne `TDS_UNKNOWN_HANDLE`, neue Zeile in der Antwort-Tabelle, Absatz zu `sp_execute`.
* Beleg `make docs-check`: grün.

**Liefer-Punkt 2, Quelltext und Tests: erfüllt.**
* `RequestLoop.executePrepared` sendet `TDS_SIM_UNKNOWN_PREPARED_HANDLE` mit State 4 über `ErrorMapper.executeUnknownHandle` (Form RPC, Log-Marke `unknown-handle`); `ErrorRule.message(handle, state)` trägt den State je Aufrufform; `ErrorCode.TDS_UNKNOWN_HANDLE` und `N_HANDLE` entfernt. Die Batch-Freigabe (`releaseAnswer`) bleibt bei State 8. `refuse` nimmt jetzt die fertige `ErrorResponse`; die Aufrufer `TDS_SHUTDOWN` und `TDS_PREPARE_LIMIT` bilden dieselbe Antwort wie zuvor.
* Tests: `PreparedHandlesDispatchTest` (nie vergeben 2, −1, 0), `UnprepareDispatchTest` (freigegeben), `ResetConnectionDispatchTest` (vor `RESETCONNECTION`, auch über den Transport), `RequestSequenceTest`, neuer Handvektor `ErrorMapperTest > sp_execute mit unbekanntem Handle ist ERROR 8179 State 4 in der Form RPC`; Gegenprobe State 8 der Batch-Freigabe bleibt in den Unprepare-Tests und ist durch V4 rot belegt. Kein Backend-Aufruf, Zähler unverändert, Verbindung offen: asserted und durch V2 rot belegt.
* Referenzclient-Tests, die 50009 erwarten: keine Fundstelle (Punkt 2 unten).
* Belege `make test` (in `clean-build`) und `make detekt` (in `clean-build` und `gates`): grün.

**Liefer-Punkt 3, Doku: erfüllt mit einer Auslegungsfrage (Punkt 3 unten).**
* Matrixzeile `sp_execute`: 8179/State 4, RETURNSTATUS, DONEPROC; Herkunftsspalte trennt gemessen (Nummer, Class, State, Text für 999999, offene Verbindung, M-7 freigegeben) von Setzung (RETURNSTATUS 8179, leere Felder, Text für andere Nummern, `RESETCONNECTION`).
* `docs/messungen/referenzabgleich.md` M-6: der Hinweis „bleiben bis dahin unverändert“ ist durch die Einlösung über ADR-0045 ersetzt. Register-Stand fortgeschrieben (`state.md`), Roadmap und Wellendatei zeigen auf `in-progress/`.

**Nachweis-Pflichten.** `make gates` grün (FORCE=1, Exit 0). Review-Report liegt vor. Haken: keine gesetzt, richtig. Doku-Update öffentlicher Vertrag: Nutzer-Doku ja; `SPEC-059`: `make flaeche` Exit 0, kein öffentlicher Typ betroffen. Closure-Notiz, Register-Schluss, Risiko-Ausgänge §6 und Paarungen: noch nicht geschrieben, Sache des Planners (§7 steht auf „Wird bei der Closure gefüllt“).

## Die Schwerpunkte

**(1) Echtheit gegen M-6, M-7 und die Rohbytes: gedeckt, Setzung ehrlich gekennzeichnet.**
* M-6 (Prozedur und Batch, Handle 999999: `Number 8179, Class 16, State 4`, Text mit Nummer, ein Fehler, Verbindung offen; 2022 und 2025) und M-7 (zwei vergebene, dann freigegebene Handles: `sp_execute` State 4) stehen im Messprotokoll, wie `SPEC-057` sie zitiert. Handle 999999 ist „nie vergeben“, M-7 „freigegeben“; der Code sendet beides mit State 4.
* Unter `referenztest-dotnet/Mitschnitt/messung/` liegt kein Mitschnitt von `sp_execute` mit unbekanntem Handle: die Treffer auf 8179 sind nur `zusatz-unprepare-curcmd` und `zusatz-unprepare-viele` (beide Instanzen, M-22/M-23 von `sp_unprepare`); die übrigen `sp_execute`-Treffer sind Hex-Zufallstreffer in Bulk-Dateien. Die Tokenfolge (ERROR, RETURNSTATUS 8179, DONEPROC mit ERROR, kein DONE) und die leeren ERROR-Felder (Servername, Prozedur, Zeile 0) sind also Setzung, und genau so geführt:
  * `SPEC-057`: „Setzung, nicht gemessen: … die Tokenfolge auf dem Draht (RETURNSTATUS 8179 …), die leeren Felder des ERROR (Servername, Prozedur, Zeile 0; beim gemessenen `sp_unprepare` trägt der ERROR Servername, die Prozedur `sp_unprepare` und Zeile 1) und der Fall nach `RESETCONNECTION`“ (der letzte Halbsatz aus `65d80508`).
  * Matrix `sp_execute`: Herkunftsspalte wie oben. Nutzer-Doku: „RETURNSTATUS, die Tokenfolge und der Fall nach `RESETCONNECTION` sind Setzung“; die leeren ERROR-Felder nennt die Nutzer-Doku nicht einzeln (INFO, kein Befund: die Doku nennt die Tokenfolge als Setzung, die Felder sind darin enthalten).
  * Code-KDoc (`ErrorCode.kt`): „Wortlaut für andere Handle-Nummern … als gleich gesetzt“; der Handvektor im `ErrorMapperTest` ist als Setzung dokumentiert. AGENTS.md §3.8 gehalten.
* Eine kleine Unschärfe, nicht meldepflichtig: `SPEC-057` zitiert bei RETURNSTATUS die „Regel für SQL-Server-Nummern in `SPEC-030`“; sie steht in `SPEC-030`/„Fehler in der Antwort auf einen RPC“ und ist bestehend (Reviewer F, Punkt b).

**(2) `TDS_UNKNOWN_HANDLE`/50009 nirgends mehr gesendet: bestätigt.** Grep `50009|TDS_UNKNOWN_HANDLE` über `src` (main, test, integrator), `testserver`, `referenztest-dotnet`, `tools`, `docs`, `spec`, `harness`, `config`, `README.md`, `AGENTS.md`: In Produktcode keine Fundstelle (nur `STATE_EXECUTE_UNKNOWN_HANDLE`, ein Namenstreffer). Verbleibend: zwei Wächter-Kommentare in Tests (nennen 50009 als Fehlerbild), `SPEC-057`-Schlusssatz und Änderungsprotokoll (Zusage „gibt es nicht mehr“), Messprotokoll, Slice-/Register-Texte, ADRs und Zeitdokumente in `done/` (nach AGENTS.md §3.5 und Zeitcharakter unberührt, auch ADR-0027 Zeile 39). `referenztest-dotnet/`, `testserver/`, `tools/`: keine Fundstelle; Risiko 4 des Slice damit „entfallen“. Die übrigen Treffer sind Hex-Zufallstreffer in Bulk-Mitschnitten.

**(3) Matrixzeile „Handle-Verwaltung je Sitzung“: nicht geändert, Auslegungsfrage für den Planner.** Die Zeile (Matrix Z. 101) trägt im Titel „unbekanntes Handle“, ihr Inhalt nennt aber weder Fehlernummer noch State noch ein Verhalten bei unbekanntem Handle (Grenze, Freigabe, Sitzungsende, Trennung der Sitzungen, abgelehnter RPC). Es gibt nichts, was falsch wäre; die Aussage steht in der Zeile `sp_execute`. Wörtlich erfüllt ist der DoD-Satz und die Zeile der ADR-Liste („Zeilen `sp_execute` und ‚Handle-Verwaltung je Sitzung‘ … führen 8179/State 4“) damit nicht; sachlich ist nichts offen. Der Implementer hat das in der Plan-Tabelle (§3) begründet (Reviewer F-1/F-5, behoben in `65d80508`). Mein Urteil: nicht ändern müssen; der Planner richtet bei der Closure entweder den DoD-Wortlaut auf „Zeile `sp_execute`“ aus oder erlaubt ausdrücklich den Verzicht (INFO). Der Planner entscheidet, ich setze nichts.

**(4) Mutationen, einzeln über enge Testklassen** (Kopie des Baums im Scratchpad, `:test` mit `--tests` auf die fünf Klassen `PreparedHandlesDispatchTest`, `ResetConnectionDispatchTest`, `RequestSequenceTest`, `ErrorMapperTest`, `UnprepareDispatchTest`; Basis grün, Exit 0):

| Nr. | Mutation | Wirkung |
|---|---|---|
| V1 | `STATE_EXECUTE_UNKNOWN_HANDLE` 4 → 8 | rot, 16 Testfälle |
| V2 | Backend wird bei unbekanntem Handle gerufen (`handles.sql(…) ?: "select 1"`) | rot, 15 Testfälle (kompiliert; echte Assert-Fehler) |
| V3 | Form `BATCH` statt `RPC` in `executeUnknownHandle` (DONE statt DONEPROC, kein RETURNSTATUS) | rot, 4 Testfälle |
| V4 | Gegenprobe: Batch-Freigabe (`releaseAnswer`) mit State 4 statt 8 | rot, 10 Testfälle |

Keine Mutation blieb grün. Die Mutationen des Reviewers (M3, M7, M8, E1, E2) habe ich nicht wiederholt.

**(5) Plan-Tabelle §3 gegen Diff.** Alle genannten Dateien erscheinen im Diff (Spec, Nutzer-Doku, `RequestLoop.kt`, `ErrorCode.kt`, `ErrorMapper.kt`, `ErrorAnswers.kt`, fünf Testdateien, Matrix, `spec-abdeckung.md`, Welle, Roadmap, Register-`state.md`, `slice-adr-…`, `slice-massenladen-…`, M-6). Der Handvektor liegt im bestehenden `ErrorMapperTest` statt in einer neuen Datei; Plan-Tabelle erlaubt „Testdatei im Paket `protocol/dispatch` oder `protocol/error`“. Zusätzlich im Diff: `ErrorCode.kt`-KDoc und `RequestLoop.kt`-KDoc (in der Tabelle), `ErrorMapper.kt` (KDoc `code`, in `65d80508` ergänzt), Nutzer-Doku-Satz zur Zahl der echt beobachteten Nummern (siehe unten). Kein Treffer in `harness/` oder `AGENTS.md` (Zellen ≤ 200 Zeichen nicht betroffen: Diff ohne `harness/README.md`; die vorhandenen langen Zellen sind nicht Teil dieses Slice). `make flaeche` grün.

## Prüfung der Nachbesserung `65d80508` gegen den Review

* F-1 (MEDIUM, Plan-Tabelle und „Berührte Spec-Stellen“ vollständig): behoben. Der Kopf nennt `SPEC-030` (Tabellenzeile, Satz, Absätze), `SPEC-022`, `SPEC-060` und die Zeilen; die Tabelle nennt `SPEC-022`/`SPEC-030`, `spec-abdeckung.md`, Welle, Roadmap, Register, `done/`- und `open/`-Verweise und die Test-Filter-Änderung.
* F-2 (LOW, KDoc `ErrorMapper.code`): behoben, KDoc nennt `executeUnknownHandle`. Reiner Kommentar.
* F-3 (LOW) und F-7 (Nutzer-Doku): Nutzer-Doku nennt die echt beobachteten Nummern („18456, 8179, 2627, 2714, 2705, 4902 und 208 (dieses mit Class 16)“); Gegenprobe gegen die Herkunftsspalten der §4-Tabelle: 2627 (M-12), 2714/2705/4902 (M-19), 208 (Messung R05, Class 16) tragen. State-Satz mit 8179 als Beispiel richtig. Behoben.
* F-5: Matrixzeile „Handle-Verwaltung“ unverändert, begründet (Punkt 3).
* Hinweis: Der Implementer hat in `65d80508` die Plan-Tabelle des Slice (§3) nachgeschrieben. Das ist erlaubt („Der Implementer-Agent erweitert die Liste in seinem ersten Lauf“); die DoD-Zeilen im Slice blieben unberührt.

## Risiken §6 (Ausgang-Vorschlag für den Planner)

1. Zweite Fundstelle: eingetreten und behoben (`docs/user/referenz-konfiguration.md`); Grep über das ganze Repo nach dem Stand `65d80508` zeigt keine weitere gesendete Fundstelle.
2. Tokenfolge und Felder sind Setzung: weiter offen als akzeptiertes Negativ der ADR (Offen 1), in Spec, Matrix, Nutzer-Doku, KDoc und Test gekennzeichnet.
3. Wanduhr-Tests: in meinem `clean-build` und in `make e2e` kein Flake aufgetreten, auch `ListenerAbortTest` PASSED; kein Befund dieses Slice (Ausgang: nicht eingetreten).
4. Client erwartet 50009: entfallen (keine Fundstelle).
`RESETCONNECTION` am echten Server: weiter offen (ADR-0045 Offen 2), Setzung gekennzeichnet.

## Gesamturteil

**Konform.** Alle drei Liefer-Punkte sind nach Beleg erfüllt; die Entscheidung (ADR-0045 Entscheidungen 1 bis 5) ist im Code, in der Spec, der Matrix und der Nutzer-Doku getragen; Messung und Setzung sind getrennt. Keine DoD-Verletzung, kein Plan-Abweichungs-Befund. Offen sind nur Planner-Aufgaben (Haken, Closure-Notiz, Auslegung Matrixzeile „Handle-Verwaltung“, Risiko-Ausgänge).

## Bedingungen für die Planner-Closure

1. Haken der drei Liefer-Punkte und der erfüllten Nachweis-Pflichten setzt der Planner jetzt (Verifikation bestätigt sie).
2. Entscheidung zu Punkt 3: DoD-Wortlaut „Zeile ‚Handle-Verwaltung je Sitzung‘“ an den Zustand angleichen oder den Verzicht in §7 notieren.
3. Closure-Notiz §7 mit Steering-Loop-Lerneintrag, Zuordnung *Messung | Festlegung* je Feld (Nummer, Class, State, Text: gemessen M-6/M-7; Tokenfolge, RETURNSTATUS, leere Felder, `RESETCONNECTION`: Setzung), Risiko-Ausgänge nach obiger Liste, Satz zur Beobachtung (Register `BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung` schließen; `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` bekommt hier einen Beleg: die zweite Fundstelle trat wieder auf und wurde im Plan vorab gefunden).
4. Reihenfolge nach AGENTS.md §3.3: erst Closure-Inhalt, dann der reine `git mv` nach `done/` und Verweis-Nachzug (`slice-mv`).
5. Vor dem Merge nach `main` die Konflikte mit dem parallelen Slice `slice-massenladen-atomar-bauen` prüfen (Fehlertabelle §4, andere Zeilen); `git merge-tree` gegen `a830d6d1` ist konfliktfrei.
6. `docs/plan/spec-abdeckung.md` ist vom Implementer erzeugt (SPEC-057 8 Tests, SPEC-060 15); ich habe sie nicht nachgerechnet und `make spec-abdeckung` nicht gefahren (kein Gate).
