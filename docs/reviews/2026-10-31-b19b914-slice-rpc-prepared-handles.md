# Review: slice-rpc-prepared-handles (Commit b19b914)

* Basis: `4a3bed0`, Branch `worktree-agent-ae8b680bdd87a9fca`.
* Prüfgrundlage: Plan, [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), SPEC-030, SPEC-013, SPEC-057, SPEC-058 und `AGENTS.md` §3.1 bis §3.8, nach `.harness/skills/reviewer.md`; nicht gegen die DoD.
* Lauf: Diff, Quellen und Logs gelesen; keine Tests und keine Gates gefahren; nichts am Branch geändert.
* Herkunft: Der Reviewer-Agent hatte keinen eigenen Worktree; der Inhalt ist sein Bericht, vom Aufrufer abgelegt.

## Gesamturteil

Kein HIGH. Die Umsetzung folgt Plan und [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) (Grenze 1000, Class 16, Verbindung offen, Backend nicht gerufen, Handles bleiben gültig). Die Vergabe ist atomar (Limit-Prüfung und Zähler im selben Lock). Das Handle wird bei nicht abgeschlossener Antwort freigegeben. Keine `@Suppress`, keine Änderung an detekt, Build oder Gates; die Slice-Moves sind reine `git mv`-Commits. Drei MEDIUM stehen vor dem Merge an.

## Findings

* **MEDIUM-1 (3.8, Maintainability), `spec/spezifikation.md` SPEC-030, letzter Spiegelstrich:** Die neue Liste „Handle-Verwaltung“ ist mitten in einen bestehenden Satz eingefügt („gegen gleichzeitigen Zugriff geschützt. Ohne RETURNSTATUS und …“). Der Rest („Ohne RETURNSTATUS und RETURNVALUE bleiben ReturnValue, OUTPUT-Parameter und Prepare-Handle … leer bzw. −1; die Tokens sind … Pflicht“) hängt als Fortsetzung am letzten Spiegelstrich der Handle-Tabelle. Ein Leser liest die Token-Pflicht der RPC-Antwort als Regel der Handle-Tabelle. `make docs-check` fängt es nicht.
* **MEDIUM-2 (Eigencode, dessen Entfernung keinen Test rot macht), `RequestLoop.kt`, `withContext(NonCancellable) { handles.clear() }`:** Kein Test trägt das `NonCancellable`. Der Mutex ist in der sequentiellen Schleife nie umkämpft; `withLock` geht im unkontendierten Fall über `tryLock` und prüft die Abbruchflagge nicht, deshalb leert `clear()` auch ohne `NonCancellable`. Aus dem kotlinx-Verhalten abgeleitet, die Mutation nicht gefahren; unter `impl/` liegt keine Mutation dafür. Verifizierbar: Zeile durch `handles.clear()` ersetzen, Suite bleibt grün.
* **MEDIUM-3 (3.8), Spec „Endet die Antwort mit einem Fehler, entsteht kein Handle“, Matrix-Zeile `sp_prepexec`:** `HandleReturningBackend` speist das Handle-RETURNVALUE vor dem ersten `OutputParameter` des Backends ein, auch wenn danach ein `Failure` folgt. Getestet ist nur der Strom, der mit `Failure` beginnt. Die Reihenfolge `OutputParameter`, dann `Failure` ist ungetestet; dort kann das Handle den Client erreichen, während der Server es freigibt. „Kein Handle“ trägt nur für den Fehler vor dem ersten Ausgabewert.
* **LOW-1 ([ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) Entscheidung 3, [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)), `PreparedHandles.kt`, „dem einen Mutex dieser Sitzung für ihre Handles“:** [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) verlangt die Tabelle „unter dem Sitzungsschutz von [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)“. Der Diff führt einen zweiten, eigenen Mutex je Tabelle ein; die KDoc nennt ihn „den einen Mutex der Sitzung“ und verdeckt die Abweichung vom Wortlaut. Ein Deadlock ist nicht erkennbar (keine Verschachtelung). Die Lesart entscheidet der Architect, etwa durch Klarstellung in der Folgepflicht.
* **LOW-2 (Maintainability), SPEC-057/058, Slice „die Nummer 053 vergibt die Anmeldedrosselung“:** Die IDs springen von 053 auf 057; 054 bis 056 sind nirgends vergeben und nicht begründet. Eine Kollision mit den Geschwisterzweigen gibt es nicht; 50009 und 50010 sind frei.
* **LOW-3 (Maintainability), `PreparedHandlesTest` „Parallele Vergabe auf einer Tabelle“:** Der Test fängt die Mutation „Mutex entfernt“ nur wahrscheinlichkeitsbasiert (20 Runden, 8 Threads, kein Zwang zur Verschränkung); einmal rot ist nicht stabil rot.
* **INFO-1:** Die Titel SPEC-025/032/050 („OHNE NACHWEIS“) stammen nicht aus dieser Änderung; die committete Matrix auf `main` war veraltet, die Neugenerierung zieht die Zeilen nach. Der Verifier bestätigt.
* **INFO-2:** Der vermutete Flake `SimulationWiringTest > ein Symlink …` steht nur in `mut2.log` („rootDir must be verified to be directory beforehand“ aus `FileTreeWalk`, Aufräumen des Temp-Verzeichnisses); in neun weiteren Läufen PASSED. Kein Zusammenhang mit dieser Änderung erkennbar, nicht reproduziert. Gehört als eigene Beobachtung ins Register.
* **INFO-3:** „sp_prepexec mit altem Handle“ und „sp_unprepare gibt nichts frei“ stehen in Spec und Matrix als Setzung. Folge: wiederholtes Neu-Präparieren belegt Handles bis zur Grenze 1000 ([ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) „Negativ“, in der Spec benannt). Der gehaltene Text ist ungrenzt.
* **INFO-4:** Abschluss-Token: gebaut ist ERROR + DONE, [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md) Folgepflicht 3 deckt das. Der Verifier prüft die Einlösung. Merge-Konfliktpotenzial an `spec/spezifikation.md` und Roadmap mit dem Zweig der Anmeldedrosselung.
* **INFO-5:** Die Mutationslogs `mut1` bis `mut5` tragen keinen Mutationstext; die Zuordnung Mutation zu Quellzeile ist nicht überprüfbar.

## Geprüft, ohne Befund

* `protocol/dispatch` (RequestLoop, RpcBinder, RpcParameters): Race Vergabe/Limit, Freigabe bei Abbruch oder Fehler, Ablehnung vor Handle-Vergabe, Fehlerzweige mit fester Log-Marke.
* `protocol/error`: 50009/50010, Class 16, offen; Bytes gepinnt.
* Kommentare gegen 3.7 und Aussagen gegen 3.8: kein Konjunktiv über Verworfenes, keine Slice-Nummern als Begründung; Setzungen benannt.
* detekt, Build, Dockerfile, tools, Makefile unverändert; Docker-only gewahrt; Moves rein (3.3).
