**Stand:** verkörpert — `.harness/skills/reviewer.md` (MEDIUM, Prüfpunkt „Zahl, Feldmenge oder Zuordnung steht an einem zweiten Ort ohne Sensor“), Anker `seit welle-betrieb-diagnose`; Nachtrag in `welle-betrieb-diagnose-results.md` §Steering-Loop-Einträge. 7 Dateien in `evidence/` (der frühere Stand-Text zählte 1×). Regel, kein neuer Sensor; die Instanz Heap und Text von `gross` hält `IntegrationZahlenTest`. Als Möglichkeit offen, nicht angelegt: ein Test, der die Felder je Art in `LogInspektion.kt` gegen `TelemetryJson.covered` liest (ob die Modulgrenze es zulässt, ist nicht geprüft).


**Lese-Schritt welle-fehler-abbruch (2026-11-06):** 5× gelesen, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung), siehe `welle-fehler-abbruch-results.md` §Steering-Loop-Einträge und §Offene Fragen. Die Closure ändert weder Regel noch Gate. Der Stand `offen` bleibt. Der Zähler im Stand-Text oben (1×) ist überholt; die Dateien zählen 5.


**Wiederauftreten (slice-qa-performance-rueckstau):** Re-Review N-3 und Re-Review 2 M-1: Zellennamen an drei Orten, ein Kommentar behauptete einen Beleg der Kopplung, den der Selbsttest nicht hatte; der Re-Review fing es per Mutation. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.

**Wiederauftreten (slice-qa-performance-heap-abschluss):** Review F-6: die Grenzen des Laufs als Literal der Zeile `Kopf` und in den `docker run`; der Reviewer fing es per Mutation. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.
