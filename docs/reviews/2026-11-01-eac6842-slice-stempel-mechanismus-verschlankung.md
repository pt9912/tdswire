# Review: Verschlankung von slice-stempel-mechanismus, zweite Fix-Runde (eac6842)

* Rolle: Reviewer (Modul 10), frischer Kontext. Der Reviewer hatte keinen eigenen Worktree; Inhalt vom Aufrufer abgelegt (verdichtet, Findings vollständig).
* Vorbefunde: Review d286322 (HIGH F-1), Verifikation d00ead2 (V-1 bis V-5, M13/M14 überlebten).
* Diff: `git diff d00ead2..eac6842`, Branch `worktree-agent-a6cd22a38ea2e2c42`, Basis `c83f5e1`, nicht gemergt. Prüfgrundlage: Plan, Entscheidungen ([ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0023](../plan/adr/0023-gate-klassen-und-stempel.md)), Hard Rules.
* Lauf: 30 Mutationen in Scratch-Kopien, Einzelmessungen unter Last (Lastmittel 15). Kein Docker-Build, kein `make gates`/`docs-check`. Hauptrepo vorher/nachher unverändert.

## Gesamturteil

Die Verschlankung hat keine Aussagekraft verloren, die eine Mutation noch tragen müsste: 24 von 30 Mutationen rot, 6 grün (drei davon echte Lücken: F-2, F-3, F-4). V-1, V-2, V-3 wirklich behoben (M13, M14 rot). Entfernung aus `GATE_CHECKS` ist ehrlich benannt (`tools/mk/stempel.mk`, `harness/README.md`, Slice §7) und keine Lockerung nach §3.6. Merge gegen `main` (a6ec9c8): sauber, kein Textkonflikt; auf dem gemergten Tree sind Prüfung, Eingänge-Selbsttest (91), Stempel-Selbsttest (112) und `stempel-abbild` (68) grün; `docs-check` dort nicht gefahren.

## Findings

* **F-1 (HIGH), Kommentar beschreibt abwesenden Zustand / verworfene Alternative (§3.7):**
  * `tools/mk/stempel.mk`: „Vor der Verschlankung (damals Gates): stempel-selbsttest 18,7 s, stempel-eingaenge 27,3 s.“ ist Chronik, gehört in `git` und Slice §7.
  * `tools/stempel-selbsttest.sh`: „ein `git reset --hard` mit `git clean -fdx` brauchte 4,6 ms: die Zeit steckt in den Skriptaufrufen, nicht im Anlegen.“ nennt die Laufzeit einer nicht gewählten Alternative statt der Zusage von `neu()`.
* **F-2 (MEDIUM), eine Gruppe kann ohne Fälle durchlaufen:** `tools/stempel-selbsttest.sh`: die Schwelle `[ "$LAEUFE" -lt 100 ]` gilt nur für die Summe bei 112. Stilles Aussetzen von `g_umschreiben_ohne` (107), `g_gleichzeitig` (108) oder `g_schreiben` (102) bleibt grün (`#ZAHLEN 0 0` genügt). Gleiches für `je_*` in `tools/stempel-abbild-selbsttest.sh` (Schwelle 40 von 68; Gruppe `pins` darf fehlen). Szenario: Umbenennung von `g_gleichzeitig` -> „Kommando nicht gefunden“, `#ZAHLEN 0 0`, Nebenläufigkeitstest weg, nichts rot. Behebung: Mindestzahl je Gruppe prüfen.
* **F-3 (MEDIUM), Eigencode ohne Test:** `tools/stempel-eingaenge-pruefung.sh`: `[ "$tiefe" -lt 10 ] || return 0` und `[ -z "${KENNT["$1:$r"]:-}" ] || stage_quellen`. Entfernt man den Schutz, bleiben alle 91 Fälle grün; ein Dockerfile mit zyklischem `COPY --from` (compile <-> test) läuft endlos (Exit 124 bei `timeout` 20 s), mit Schutz Exit 0. Auch die Stage-unbekannt-Prüfung hat keinen Fall. Behebung: Fall „zyklisches COPY --from“ und Fall „unbekannte Stage“.
* **F-4 (LOW), Text-Assert statt Verhaltens-Assert:** `ist "Standard der Sperre: 20 Versuche (im Skript)" grep -q 'STEMPEL_SPERRE_VERSUCHE:-20'` belegt den Wortlaut, nicht dass die Variable wirkt; 5 vs. 20 Versuche trennt die Wartezeitprüfung (400 bis 10 000 ms) nicht. Behebung: Verhalten prüfen (z. B. Wartezeit mit `STEMPEL_SPERRE_VERSUCHE=1` vs. Standard) oder die Aussage auf den Wortlaut einschränken.
* **F-5 (LOW), Messbedingung weicht ab:** Kommentar „ohne Sperre: Einzelmessung 4 von 6 Laeufen rot bei je 40 Stempeln“, der Fall fährt 2 mal 10. Reviewer-Messung: 5 von 6 rot bei 10 (mit Mutation `mkdir -p` immer erfolgreich), mit Sperre 8 von 8 grün. Bedingung in den Satz.
* **F-6 (LOW), Zahlen an zwei Orten uneinheitlich:** `tools/mk/stempel.mk` „stempel-eingaenge … 2,6 s“, „Lastmittel 7 bis 10“ vs. Slice „27,3 s auf 2,9 s“, „Lastmittel 7 bis 16“. Reviewer: 3,0 s Selbsttest, 1,8 s `stempel-selbsttest` bei Lastmittel 15. Vereinheitlichen (eine Fundstelle, die andere verweist).
* **INFO 1:** `stempel.mk`-Kopf sagt nicht ausdrücklich, dass ein Fehler in `tools/stempel*` bis zur Migration kein Gate rot macht (aus „kein Gate“ ableitbar; bewusste Setzung des Nutzers).
* **INFO 2:** DoD-Wortlaut („Selbsttest im Werkzeug-Gate“, „`make gates` grün einschließlich der neuen Selbsttests“) weicht ab; Slice §7 nennt das selbst. Zuständig: Planner vor der Closure.
* **INFO 3:** `make docs-check` auf Branch/gemergtem Tree nicht gefahren; Zellenlängen (165, 158, 173 Zeichen) von Hand gemessen. Zuständig: Verifier.

## Geprüft ohne Befund

`ls-tree`-Umstellung (Sonderzeichen, Symlink, Unterverzeichnis, `.`, fehlende Pfade; M15/M16 rot), Sperre (M10/M11), Schreib- und Nachlesepfad (M13/M14), V-1-Rekursion (bis auf F-3), `stempel-abbild` berührt weder `refs/notes/gates` noch die Konfiguration, `harness/README.md`-Zeilen und Links, keine Accepted-ADR berührt (§3.5), kein `@Suppress` (§3.2), Host-Läufe begründet (§3.1).
