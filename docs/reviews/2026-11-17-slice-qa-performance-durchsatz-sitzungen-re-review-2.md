# Review-Report: slice-qa-performance-durchsatz-sitzungen, Re-Review 2 des Werkzeugs `tools/lasttest-tabellen.sh` — 2026-11-17

**Review-Art:** Re-Review in frischem Kontext nach `.harness/skills/reviewer.md` (Abschnitt „Nachbesserung mit Code ohne zweiten Review“); gelesen wird nur der Diff `git diff 5278c468..HEAD` (ein Commit, `fb0173fc`), Nachbesserung zum Verifikationsbefund V-1 des [Verifikationsberichts](2026-11-17-slice-qa-performance-durchsatz-sitzungen-verifikation.md) (Nutzerentscheidung 2026-11-17: Aggregator ins Repo).

**Gegenstand:** `tools/lasttest-tabellen.sh`, `tools/lasttest-tabellen-selbsttest.sh`, `tools/mk/lasttest.mk`, `Dockerfile` (Stage `skript-selbsttest`), `harness/README.md` (eine Zeile), [Messprotokoll](../messungen/performance-durchsatz-2026-11-17.md) (Erzeuger, Grenze, V-2, V-5), [Rohzeilen](../messungen/performance-durchsatz-2026-11-17-rohzeilen.txt).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-17

**Eingangs-Kontext:** [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), `AGENTS.md` §3.1, §3.7, §3.8, vorherige Reports des Slice ([Review](2026-11-17-slice-qa-performance-durchsatz-sitzungen.md), [Re-Review](2026-11-17-slice-qa-performance-durchsatz-sitzungen-re-review.md)).

**Selbst gefahren** (Host mit fremden Läufen, keine künstliche Last, kein `make lasttest`, kein `clean-build`, kein `e2e`):

- `bash tools/lasttest-tabellen.sh <Rohzeilen> stufe1b stufe8 stufe16 stufe64` (139 Zeilen) gegen die Zeilen 23 bis 161 des Protokolls: `diff` zeigt nur eine Leerzeile am Anfang bzw. Ende des Ausschnitts (Schnittgrenze), **sonst keine Abweichung**. Gleiche Ausgabe im Container des Basis-Images (`gradle:8.14-jdk21`, awk = mawk) und auf dem Host (awk = gawk): beide Dialekte liefern byte-gleiche Tabellen.
- Selbsttest: `bash tools/lasttest-tabellen-selbsttest.sh` Exit 0 (0,13 s); `docker build --no-cache --target skript-selbsttest .` Exit 0, 32 s, Ausgabe `lasttest-tabellen-selbsttest: ok (Tabellen am Muster, Filter, zwei Eingabefehler, 6 Mutationen rot)`.
- Eigene Mutationen an Kopien im Scratchpad, je gegen den ganzen Selbsttest: Filter nach Stufenname entfernt: rot, nur „Filter nach Stufenname“; Exit 1 bei „keine Zelle“ auf 0: rot, nur „Datei ohne Zelle wurde angenommen“; Spalte „nachher“ der Last durch „vorher“ ersetzt: rot, nur „Tabellen am Muster“; nur `sauber()` auf immer wahr: rot (die Zeile „1 von 1, gestört“ fehlt); `%.2f` zu `%.1f`: rot. Die Prüfung der fehlenden Datei durch `[ -f ]` zu `:` bleibt grün (awk bricht bei fehlender Datei selbst mit Exit 2 ab, gleichwertig, kein Befund); Vergleich der Sitzungszahlen als Zeichenketten bleibt grün (die Werte sind Zahlen, gleichwertig). Die sechs Mutationen des Selbsttests einzeln gegen die Ausgabe der Beispieldatei verglichen: jede ändert genau die zu ihr gehörende Zeile (Versuchswahl, Schwelle, „(Last-Sitzungen)“, Reihenfolge, Zeilen/s je Sitzung, Millisekunden).
- `make gate-zuordnung` Exit 0 (33 Fälle), `make stempel-eingaenge-pruefung` Exit 0, `make lasttest-tabellen` ohne `ROHZEILEN` Exit 2 mit Meldung.
- Zelle der `harness/README.md`: Spalte „Tut was“ 158 Zeichen (ohne Rand), ≤ 200.
- `make docs-check` auf dem Stand mit diesem Report: Exit 0 (siehe Abschluss).

## Ergebnis der gefragten Punkte

1. **Reproduktion:** ohne Abweichung (oben). Auch V-2 („bis etwa 7 %“: größte Differenz Export ohne Senke Stufe 8, C 211 522 gegen B 198 029 = +6,8 %) und V-5 (Zusatz „nur mit Senke“) stimmen mit Rohzeilen und Werkzeug.
2. **Versuchswahl:** Das Werkzeug wählt für Stufe 8, B, mit Senke, `punkt-export` Versuch 2 (Probe 0 über 300 %), weil Versuch 1 eine Zeile `Probe: nicht zugehörig …` trägt und damit nicht als sauber gilt. Das Ergebnis stimmt mit der Tabelle („2 von 2“, p95 2,80 ms). Die **Begründung im Protokoll** passt nicht zur Regel des Werkzeugs, siehe R-1.
3. **Selbsttest:** sechs Mutationen rot, keine grün, frischer Build grün. Die Aussage „je rot“ im Kopf trägt; dass jede *genau eine* Prüfung röte, ist durch den Aufbau nicht unterscheidbar (eine Sammelprüfung `zeilenpruefung`), von mir an der Ausgabe je Mutation bestätigt.
4. **Docker-only:** die Stage führt den Selbsttest im Container aus; das Target `lasttest-tabellen` selbst ruft `bash tools/lasttest-tabellen.sh` auf dem Host (wie `make lasttest` für `tools/lasttest.sh`). Kein `gradle`/`java`/Kotlin auf dem Host, §3.1 nicht verletzt; die Host-Abhängigkeit von `awk` steht nirgends, siehe R-4.
5. **Eingabefehler, Umlaute, Sortierung, Rundung:** fehlende Datei, kein Argument, unbekannter Stufenname (keine Zelle) enden mit Exit 1 und Meldung; Umlaute („über“, „gestört“, „–“) laufen unter `LC_ALL=C` byteweise und sind in mawk und gawk gleich; Stufen sind numerisch sortiert; Rundung der Mikrosekunden auf zwei Stellen am Muster belegt; Zellen ohne `messung:`-Zeile fehlen in der Tabelle ohne Meldung (siehe R-5).
6. **Eingänge, Kommentare:** `tools/stempel/werkzeug.eingaenge` führt `tools` als Ganzes, die Stempelprüfungen sind grün; Befunde zu Kommentaren und Aussagen unten.

## Findings

| ID | Kategorie | Quelle | Pfad · Anker | Befund | verifizierbar | klasse |
|---|---|---|---|---|---|---|
| R-1 | MEDIUM | [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), `AGENTS.md` §3.8 | `docs/messungen/performance-durchsatz-2026-11-17.md` · „der gewählte Versuch ist also nicht anhand der Probe begründet“ | Das Werkzeug wählt „ersten Versuch mit höchstens einer Probe über 300 %, sonst den letzten“; Versuch 1 scheidet wegen der fehlenden zugehörigen Probe aus, Versuch 2 ist sauber: die Wahl ist durch die Probe begründet, das Protokoll sagt das Gegenteil. Die Regel und die Spalte „Versuch“ stehen nur im Kopf des Skripts, nirgends im Protokoll (Grep auf „erste Versuch“, „höchstens eine“: kein Treffer); ein Leser des Protokolls kann „2 von 2“ und „1 von 1, gestört“ nicht deuten. Ähnlich Zeile „Versuch 1 … ist ein Lauf mit anderem Ergebnis“ unter „Nicht belegt“. | ja (Grep, Lauf) | Fortschreibung lässt die zweite Fundstelle stehen |
| R-2 | MEDIUM | Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“ | `docs/plan/planning/in-progress/slice-qa-performance-durchsatz-sitzungen.md` · (kein Treffer auf `lasttest-tabellen`) | Der Slice-Plan nennt weder `tools/lasttest-tabellen.sh`, den Selbsttest, `tools/mk/lasttest.mk`, das `Dockerfile` noch die Zeile in `harness/README.md` als berührte Dateien (Grep: 0 Treffer). Die Closure-Notiz muss die Fläche nennen. | ja (Grep) | Plan-Tabelle ohne Diff-Abgleich |
| R-3 | LOW | `AGENTS.md` §3.7 (Klasse Kopplung) | `tools/lasttest-tabellen.sh` · „Gehalten von tools/lasttest-selbsttest-tabellen.sh“ | Der Kopplungszeiger nennt eine Datei, die nicht existiert; der Selbsttest heißt `tools/lasttest-tabellen-selbsttest.sh`. | ja (`ls`) | Kopplungskommentar zeigt ins Leere |
| R-4 | LOW | `AGENTS.md` §3.1, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) | `tools/mk/lasttest.mk` · „bash tools/lasttest-tabellen.sh “ | Das Target läuft auf dem Host mit `bash` und `awk`; die Zeile in `harness/README.md` und der Kopf des Skripts nennen das nicht (der Kopf des Selbsttests nennt die Werkzeuge nur für den Container). `awk` steht nicht unter den Host-Voraussetzungen von `harness/conventions.md` (`bash`, `git`, `docker`). Beide awk-Dialekte (mawk, gawk) gemessen gleich. | nein | Host-Abhängigkeit nicht benannt |
| R-5 | LOW | `AGENTS.md` §3.7 | `tools/mk/skript-selbsttest.mk` · „sowie der Statistik und der Zahlenwerte der Umgebung des Lasttests“ und die `##`-Hilfezeile | Kopf und Hilfetext des Targets zählen die geprüften Skripte auf und nennen `tools/lasttest-tabellen.sh` nicht, obwohl die Stage es jetzt hält; die Zeile in `harness/README.md` (`lasttest*.sh`) deckt es per Muster. | ja (Lesen) | Fortschreibung lässt die zweite Fundstelle stehen |
| R-6 | INFO | Maintainability | `tools/lasttest-tabellen.sh` · `if (!(idx in zeile)) …` / `waehle` | Eine Zelle ohne passende `messung:`-Zeile (abgebrochener Lauf) fehlt in der Ausgabe ohne Meldung; das entspricht der Aussage des Protokolls („abgebrochene Läufe sind nicht in den Zahlen“), ist aber eine stille Auslassung. Zwei Abschnitte mit gleichem Namen überschreiben sich, die Zahl „von n“ zählt beide. Kein Fall im vorhandenen Bestand. | nein | stille Auslassung |
| R-7 | INFO | Maintainability | `tools/lasttest-tabellen-selbsttest.sh` · `sauber(` | Der Zweig „gestört“ bei zwei oder mehr Proben über 300 % in allen Versuchen ist im Beispiel nur über „k. A.“ belegt; das Entfernen von `sauber()` wird rot, eine Mutation an der Schwelle nur in `sauber()` bleibt grün, solange `waehle` sie trägt. Kein Fehler im Bestand (alle gestörten Zellen sind in der Ausgabe gleich). | ja (Mutation) | Mutation ohne eigene Prüfung |

## Negativbefund (geprüft, ohne Befund)

- `tools/` (Skript, Selbsttest): geprüft, ohne Befund außer R-3, R-6, R-7; keine Unterdrückung, keine Host-Toolchain, Kommentare tragen Zusage, Kopplung, Abgrenzung oder Grenze (§3.7); Aussagen im Kopf sind an der Ausgabe belegt (§3.8).
- `tools/mk/` (Target, Kein-Gate-Einordnung): geprüft, ohne Befund außer R-4, R-5; das Target steht nicht in `GATE_CHECKS`, `make gate-zuordnung` und `make stempel-eingaenge-pruefung` grün.
- `Dockerfile` (Stage `skript-selbsttest`): geprüft, ohne Befund; beide neuen Dateien in `COPY`, Selbsttest an der `&&`-Kette, frischer Build grün.
- `harness/README.md`: geprüft, ohne Befund; Zeile vorhanden, Bindung `kein Gate`, Länge ≤ 200.
- `docs/messungen/`: Tabellen ohne Abweichung zur Ausgabe; V-2, V-5 und die Erzeuger-Aussage tragen; Befund nur R-1.
- `docs/plan/` (Slice): R-2.
- `spec/`, `docs/user/`: nicht berührt, nicht geprüft.

## Abschluss

HIGH: 0 · MEDIUM: 2 (R-1, R-2) · LOW: 3 (R-3, R-4, R-5) · INFO: 2 (R-6, R-7). Die Nachbesserung erfüllt die DoD-Zeile „jede Zahl mit Erzeuger im Repo“ für die Tabellen (V-1); die Fremdlast-Probe bleibt als nicht reproduzierbar benannt. `make docs-check` auf dem Stand mit diesem Report: Exit 0.
