**Stand:** verkörpert — `.harness/skills/reviewer.md` (MEDIUM-Liste), Anker `seit welle-transport-tls`; Nachtrag in `welle-transport-tls-results.md` §Steering-Loop-Einträge


**Wiederauftreten (slice-qa-performance-durchsatz-sitzungen):** Review F-8: drei Teile des neuen Eigencodes in `tools/lasttest.sh` ohne Test, der bei Entfernung rot wird; der Reviewer fand sie per Mutation. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.


**Wiederauftreten (slice-qa-performance-rueckstau):** Review F-1 (ein Test, der nie lief, weil er einen Wert zurückgab) und F-5 (Mutation `delay` statt `Thread.sleep` blieb grün); der Reviewer fand es per Mutation. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.

**Wiederauftreten (slice-qa-performance-heap-abschluss):** Review F-1, F-2, Re-Review N-1, Verifikation V-1: eine Formel, ein ungetesteter Aufbau (neun von 18 Zellen) und ein Aufruf ohne rot gesehenen Test; der Aufruf (N-1) bleibt offen, die Aussage darauf ist eingeschränkt. Retirement-Check: die Klasse ist wiedergekehrt, die Regel bleibt.
