**Stand:** verkörpert — Nutzerentscheid 2026-11-06 („deine Empfehlungen“, Regel statt Sensor): Messzahlen aus einem Erzeuger außerhalb des Repos tragen nur als Größenordnung. Vier Belege (Dateien in `evidence/`). Die Neubeschaffung der Wortlaute tragen die Folge-Slices; die Gegenmessung am echten Server liegt bei `BEO-TDS/messung-am-echten-sql-server-fehlt`. Die Messklasse „Client gegen gebautes Produkt“ wird hier mitgezählt, nicht als eigene Klasse geführt (Nutzerentscheid 2026-11-06).

- liegt in `.harness/skills/reviewer.md` (MEDIUM, Prüfpunkt „Messzahl aus einem Erzeuger, der nicht im Repo liegt“) — Auslöser: `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (slice-adr-handle-grenze-ersatz, slice-adr-testserver-bestand-und-katalog, slice-referenzabgleich-live, slice-katalog-und-ddl-zustand — 4×).
- liegt in `.claude/commands/plan-welle.md` (Schritt 6d: der Plan eines messenden Slice nennt, ob der Erzeuger im Repo liegt).
- liegt in `.claude/commands/implement-slice.md` (Schritt 20a: das Messprotokoll nennt, ob der Erzeuger im Repo liegt).

**Steering-Loop-Eintrag der Welle:** Der Eintrag mit `liegt in` gehört in die `-results.md` der Wellen, die den Lese-Schritt tragen: `welle-testserver-manuell` (drei der vier Belege) und `welle-referenzabgleich-release` (`slice-referenzabgleich-live`), sobald sie schließen; beide haben noch keine Results-Notiz. Der Lese-Schritt von `welle-fehler-abbruch` (3×) blieb ohne Verkörperung (`welle-fehler-abbruch-results.md` bleibt unverändert). Die Anker-Paarung sucht die Ziele ab Repo-Wurzel und findet den Anker in allen drei Dateien. Grenze: der Sensor erzwingt den Anker nur für deklarierte Regeln.


**Wiederauftreten (slice-qa-performance-durchsatz-sitzungen):** Review F-1 und Verifikation V-1: Tabellen und Fremdlast-Probe eines Messprotokolls aus Erzeugern außerhalb des Repos, gegen den DoD-Wortlaut „jede Zahl mit Erzeuger im Repo“. Die Tabellen setzt seit `fb0173fc` `tools/lasttest-tabellen.sh` (Selbsttest in `make skript-selbsttest`); die Probe bleibt außerhalb, benannt als nicht reproduzierbar. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.


**Wiederauftreten (slice-qa-performance-rueckstau):** Review F-2: Tabellen von Hand trotz DoD-Wortlaut „Erzeuger im Repo“, zwei Fehler der Handarbeit; behoben durch `tools/rueckstau-tabellen.sh`, die Fremdlast-Probe liegt im Repo. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.
