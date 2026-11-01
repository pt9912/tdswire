# Verifikationsbericht: slice-rpc-prepared-handles (Commit 6e481cd)

* Rolle: Verifier (Modul 11), frischer Kontext. Branch `worktree-agent-ae8b680bdd87a9fca`, HEAD `6e481cd`, Basis `main` (`4a3bed0`); nicht gemergt.
* Geprüft gegen: Slice-Plan und DoD (`docs/plan/planning/done/slice-rpc-prepared-handles.md`), [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) (Accepted), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), SPEC-030, SPEC-013, SPEC-057, SPEC-058, `AGENTS.md`, [Review b19b914](2026-10-31-b19b914-slice-rpc-prepared-handles.md).
* Eingang war die Zusage des Implementers; die Belege hat der Verifier selbst erhoben (Quelltext und Tests gelesen, `make gates` gefahren, drei Mutationen gefahren).
* Herkunft: Der Verifier-Agent durfte die Datei nicht anlegen; der Inhalt ist sein Bericht, vom Aufrufer verdichtet abgelegt.

## 1. Eigene Sensor-Läufe

* `make gates` im Worktree: Exit 0; 799 Tests PASSED, kein FAILED; alle 17 Testfälle aus `PreparedHandlesTest`, `PreparedHandlesDispatchTest` und `HandleReturningBackendTest` PASSED; Worktree danach unverändert.
* M1 (in `RequestLoop.prepare` die Bedingung `&& !returning.returned` entfernt): `make test` Exit 2, `PreparedHandlesDispatchTest > Ein Fehler nach dem Handle laesst das gesendete Handle gueltig` rot.
* M2 (`PreparedHandles.register` `>=` durch `>`, Grenze 1001): Exit 2; rot sind `Das 1001 Handle wird abgelehnt…`, `Die Tabelle haelt hoechstens 1000 Handles…` und `Parallele Vergabe…`.
* M3 (`handles.clear()` im `finally` von `RequestLoop.run` entfernt): Exit 2; rot sind `Die Handles enden mit der Sitzung` und `Zwei offene Sitzungen sehen die Handles der anderen nicht`.
* Nach jeder Mutation `git checkout --`, `git diff` leer.

## 2. DoD-Punkte

* **Liefer-Punkt 1, Handle-Tabelle je Sitzung: teilweise bestätigt.** Bestätigt sind Tabelle je Schleife (`RequestLoop.handles`), Vergabe, Nachschlagen und Ende (`clear` im `finally`, durch M3 gewächtert), Isolation zweier Sitzungen, Grenze 1000 als Setzung (nicht konfigurierbar) und atomare Limit-Prüfung (im selben `withLock`). Nicht wörtlich erfüllt ist „unter dem Sitzungsschutz des Zustandsmodells von [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)“ ([ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) Entscheidung 3): `PreparedHandles` hat einen eigenen `Mutex`, nicht den von `HandshakeSession`; die KDoc benennt das offen, ein Deadlock ist nicht erkennbar. Die Lesart entscheidet der Planner.
* **Liefer-Punkt 2, `sp_prepexec` und `sp_execute`: bestätigt.** Handle als RETURNVALUE mit Ordinal 0, `INTN` 4, Name `@handle`, vor dem ersten Backend-Ausgabewert, sonst am Ende (Handvektor `26 04 04`); `sp_execute` mit gemerktem Text und neuen Werten; fortlaufende Vergabe ab 1, der Wert des Clients (−1 oder altes Handle) bleibt unberücksichtigt; NULL und OUTPUT wie `sp_executesql`; `sp_executesql` vergibt kein Handle. Die Ordinal-Zählung ist als nicht am echten Server gemessen gekennzeichnet.
* **Liefer-Punkt 3, definierte Fehler: bestätigt, mit einer Lücke im Abbruchpfad.** Unbekanntes Handle (2, −1, 0): `TDS_UNKNOWN_HANDLE` 50009, Class 16, Verbindung offen, Backend nicht gerufen. 1001. Handle: `TDS_PREPARE_LIMIT` 50010, Class 16; 1000 Aufrufe ohne Fehler, danach laufen `sp_execute(1)` und `sp_execute(1000)`. `sp_unprepare` (15) und `sp_prepare` (11): `TDS_UNSUPPORTED_RPC` 50002, Handle bleibt gültig. Handles enden mit der Sitzung. Fehler vor dem Handle: kein Handle, Wert nicht wieder vergeben. Setzung „gesendet heißt gültig“ durch M1 gewächtert. Lücke: kein Test für den Abbruchpfad eines `sp_prepexec` (ATTENTION, Cancel, Timeout); durch Plan §6 abgedeckt, Ausgang „weiter offen“.
* **Nachweis-Pflichten:** `@spec`-Tags an jedem neuen Test; Review-Report liegt vor; SPEC-030, SPEC-013, SPEC-057, SPEC-058, Fehlercode-Abschnitt und Kompatibilitätsmatrix sind nachgezogen. Offen bis zur Closure: Closure-Notiz (Zuordnung *Test | Festlegung*, Lerneintrag; §7 leer), Beobachtungs-Register, Ausgang je Risiko aus §6, DoD-Häkchen.

## 3. Entscheidungs-Konformität

* [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md): Entscheidungen 1, 2 und 4 konform, Entscheidung 3 im Wortlaut abweichend; Folgepflichten 1 und 2 erfüllt; Folgepflicht 3: gebaut ist ERROR + DONE mit Fehlerstatus, [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) steht auf Proposed; der Nachzug auf DONEPROC bleibt an die Annahme gebunden.
* [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md): Katalog unverändert, `sp_prepare`/`sp_unprepare` bleiben nicht gebaut, der Server hält Text und Wertparameter, nicht die Deklarationsliste; `QueryBackend` unverändert, das Handle läuft über den Wrapper `HandleReturningBackend`.
* [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)/0006/0007: Zustandsautomat unverändert, Fehlerpfad über `ErrorMapper.code(...)` und `requestFinished`.
* Plan gegen Code: alle geplanten Orte berührt, Drifts im Slice §3 benannt; `build.gradle.kts`, `config/`, `Makefile`, `tools/`, `Dockerfile` unverändert; kein `@Suppress`; Moves rein (3.3); `spec/architecture.md` unberührt (3.4).

## 4. Kommentare und Aussagen

KDoc indikativ, keine Chronik, Setzungen und „nicht gemessen“ gekennzeichnet. Ausnahme F-2.

## 5. Findings

Kein HIGH, kein MEDIUM.

* **F-1 (LOW):** Der Slice-Plan nennt noch `Proposed`, wo der Index `Accepted` führt: §2 Liefer-Punkt 1 ([ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md)), §6 Risiko 4 ([ADR-0004](../plan/adr/0004-session-zustandsautomat.md)), §6 Risiko 1. Planner-Korrektur.
* **F-2 (LOW, 3.8):** `HandleReturningBackend.returned` sagt „Wahr, sobald das Handle-Ereignis in den Strom gegeben wurde“; am Stromende (`if (!returned && !failed) emit(handleEvent)`) wird `returned` nicht gesetzt. Folgt dort ein Ausgang ungleich `COMPLETED`, gibt `RequestLoop.prepare` ein vermutlich gesendetes Handle frei; in den gelesenen Pfaden schließt die Verbindung ohnehin, ein Fall mit offener Verbindung ist nicht erkennbar und ungetestet. `returned` am Stromende setzen oder die KDoc einschränken.
* **F-3 (LOW):** Lesart „unter dem Sitzungsschutz“: eigener Tabellen-`Mutex` als zulässige Lesart in der Closure-Notiz festschreiben, oder [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) Entscheidung 3 per Folge-ADR präzisieren (Accepted, nicht überschreiben, 3.5).
* **F-4 (LOW):** Wiederholtes Re-Prepare mit demselben Text belegt weiter Plätze bis 1000 ([ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) „Negativ“, in der Spec benannt); Ausgang in §6 aufnehmen.
* **F-5 (LOW):** SPEC-ID-Lücke 054 bis 056 (SPEC-057/058 nach 053); im Slice als Setzung gegen Merge-Kollision benannt.
* **F-6 (LOW):** Ob der Parallel-Test die Mutation „Mutex entfernt“ stabil fängt, ist nicht gefahren.
* INFO: Der vermutete Flake `SimulationWiringTest > ein Symlink …` trat im Gate-Lauf nicht auf. Merge-Konfliktpotenzial an `spec/spezifikation.md` und der Roadmap mit dem Zweig der Anmeldedrosselung.

## 6. Risiken aus Slice §6, vorgeschlagene Ausgänge

* Grenzwert und Vergabe: entfallen/entschieden ([ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) Accepted, Setzung, per Test gepinnt).
* `sp_unprepare`/`sp_reset_connection` unter Pooling: weiter offen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
* Handle-RETURNVALUE gegen einen echten Server: weiter offen (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
* Nebenläufigkeit/ATTENTION während `sp_prepexec`: Mutex entschieden (F-3), ATTENTION-Pfad ungetestet und Sache der Fehler-/Abbruch-Welle, weiter offen.
* Speicher je Handle (Textgröße ungrenzt): weiter offen.

## 7. Gesamturteil

**Closure-fähig: ja, unter Bedingungen.** Keine DoD-Verletzung; alle drei Liefer-Punkte sind im Code und in den Tests belegt, `make gates` grün, drei Wächter-Mutationen rot. Bedingungen (Closure-Pflichten, keine Nachbesserung am Code außer F-2): Closure-Notiz mit Zuordnung *Test | Festlegung* und Lerneintrag; Ausgang je Risiko; Beobachtungs-Register fortschreiben; `Proposed`-Angaben im Slice korrigieren (F-1) und die Lesart „Sitzungsschutz“ entscheiden (F-3); empfohlen vor dem Merge F-2; der Nachzug auf DONEPROC ist ein Folgepunkt an [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md).
